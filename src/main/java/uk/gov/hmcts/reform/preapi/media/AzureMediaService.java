package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.JobInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingEndpointInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingLocatorInner;
import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventActionInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
public class AzureMediaService implements IMediaService {
    private final String resourceGroup;
    private final String accountName;
    private final String ingestStorageAccount;
    private final String finalStorageAccount;
    private final String environmentTag;

    private final AzureMediaServices amsClient;
    private final AzureFinalStorageService azureFinalStorageService;

    private static final String LOCATION = "uksouth";
    private static final String ENCODE_TO_MP4_TRANSFORM = "EncodeToMP4";

    @Autowired
    public AzureMediaService(
        @Value("${azure.resource-group}") String resourceGroup,
        @Value("${azure.account-name}") String accountName,
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${azure.finalStorage.accountName}") String finalStorageAccount,
        @Value("${platform-env}") String env,
        AzureMediaServices amsClient,
        AzureFinalStorageService azureFinalStorageService) {
        this.resourceGroup = resourceGroup;
        this.accountName = accountName;
        this.ingestStorageAccount = ingestStorageAccount;
        this.finalStorageAccount = finalStorageAccount;
        this.environmentTag = env;
        this.amsClient = amsClient;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        try {
            return  new AssetDTO(amsClient.getAssets().get(resourceGroup, accountName, assetName));
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Asset with name: " + assetName);
            }
            throw e;
        }
    }

    @Override
    public List<AssetDTO> getAssets() {
        return amsClient
            .getAssets()
            .list(resourceGroup, accountName)
            .stream()
            .map(AssetDTO::new)
            .toList();
    }

    @Override
    public String playLiveEvent(UUID liveEventId) {
        assertLiveEventExists(liveEventId);
        String hostname;
        try {
            hostname = getStreamingEndpointHostname(liveEventId);
            amsClient.getStreamingEndpoints().start(resourceGroup, accountName, getShortenedLiveEventId(liveEventId));
        } catch (Exception ex) {
            Logger.getAnonymousLogger().info("Error creating streaming endpoint: " + ex.getMessage());
            throw ex;
        }

        assertStreamingLocatorExists(liveEventId);


        var paths = amsClient.getStreamingLocators()
                             .listPaths(resourceGroup, accountName, getSanitisedLiveEventId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(hostname, paths);
    }

    private String getStreamingEndpointHostname(UUID liveEventId) {
        Logger.getAnonymousLogger().info("creating streaming endpoint");
        try {
            return createStreamingEndpoint(liveEventId).hostname();
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error creating streaming endpoint: " + e.getMessage());
            throw e;
        }
    }

    private StreamingEndpointInner createStreamingEndpoint(UUID liveEventId) {
        var streamingEndpointName = getShortenedLiveEventId(liveEventId);
        try {
            return amsClient.getStreamingEndpoints()
                            .create(
                                resourceGroup,
                                accountName,
                                streamingEndpointName,
                                new StreamingEndpointInner()
                                    .withLocation("UK South")
                                    .withTags(Map.of(
                                        "environment", this.environmentTag,
                                        "application", "pre-recorded evidence",
                                        "businessArea", "cross-cutting",
                                        "builtFrom", "azure portal"
                                    ))
                                    .withDescription(
                                        "Streaming Endpoint for " + streamingEndpointName
                                    )
                            );
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 409) {
                return amsClient.getStreamingEndpoints()
                                .get(resourceGroup, accountName, streamingEndpointName);
            }
            throw e;
        }
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {

        try {
            Logger.getAnonymousLogger().info("Creating Streaming locator");
            var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
            var streamingLocatorProperties = new StreamingLocatorInner()
                .withAssetName(sanitisedLiveEventId)
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .withStreamingLocatorId(liveEventId);

            amsClient.getStreamingLocators().create(resourceGroup,
                                                    accountName,
                                                    sanitisedLiveEventId,
                                                    streamingLocatorProperties);
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 409) {
                Logger.getAnonymousLogger().info("Streaming locator already exists");
                return;
            }
            throw e;
        }
    }

    private void assertLiveEventExists(UUID liveEventId) {
        var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
        try {
            var liveEvent = amsClient.getLiveEvents().get(resourceGroup, accountName, sanitisedLiveEventId);
            if (!liveEvent.resourceState().equals(LiveEventResourceState.RUNNING)) {
                throw new LiveEventNotRunningException(sanitisedLiveEventId);
            }
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new AMSLiveEventNotFoundException(sanitisedLiveEventId);
            }
            throw e;
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(String streamingEndpointHostname,
                                                               ListPathsResponseInner paths) {
        Logger.getAnonymousLogger().info("parsing live output url from streaming locator paths");
        Logger.getAnonymousLogger().info(streamingEndpointHostname);
        paths.streamingPaths().forEach(p -> Logger.getAnonymousLogger().info(p.paths().toString()));
        return paths.streamingPaths().stream()
            .flatMap(path -> path.paths().stream())
            .findFirst()
            .map(p -> "https://" + streamingEndpointHostname + p)
            .orElseThrow(() -> new RuntimeException("Unable to create streaming locator"));
    }

    private String getSanitisedLiveEventId(UUID liveEventId) {
        return liveEventId.toString().replace("-", "");
    }

    private String getShortenedLiveEventId(UUID liveEventId) {
        return getSanitisedLiveEventId(liveEventId).substring(0, 23);
    }

    @Override
    public LiveEventDTO getLiveEvent(String liveEventName) {
        try {
            return new LiveEventDTO(amsClient.getLiveEvents().get(resourceGroup, accountName, liveEventName));
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Live event: " + liveEventName);
            }
            throw e;
        }
    }

    @Override
    public List<LiveEventDTO> getLiveEvents() {
        return amsClient
            .getLiveEvents()
            .list(resourceGroup, accountName)
            .stream()
            .map(LiveEventDTO::new)
            .toList();
    }

    @Override
    @Transactional(dontRollbackOn = Exception.class)
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    public RecordingStatus stopLiveEvent(CaptureSessionDTO captureSession, UUID recordingId)
        throws InterruptedException {
        var recordingNoHyphen = getSanitisedLiveEventId(recordingId);
        var recordingAssetName = recordingNoHyphen + "_output";
        var captureSessionNoHyphen = getSanitisedLiveEventId(captureSession.getId());

        createAsset(recordingAssetName, captureSession, recordingId.toString(), true);
        encodeToMp4(captureSessionNoHyphen, recordingAssetName);
        checkEncodeComplete(captureSessionNoHyphen);
        var status = azureFinalStorageService.doesIsmFileExist(recordingId.toString())
            ? RecordingStatus.RECORDING_AVAILABLE
            : RecordingStatus.NO_RECORDING;

        stopAndDeleteLiveEvent(captureSessionNoHyphen);
        stopAndDeleteStreamingEndpoint(captureSessionNoHyphen.substring(0, 23));
        deleteStreamingLocator(captureSessionNoHyphen);
        deleteLiveOutput(captureSessionNoHyphen, captureSessionNoHyphen);

        return status;
    }

    @Override
    @Transactional(dontRollbackOn = Exception.class)
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #captureSession.id)")
    public String startLiveEvent(CaptureSessionDTO captureSession) throws InterruptedException {
        var liveEventName = getSanitisedLiveEventId(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventAms(liveEventName);
        createAsset(liveEventName, captureSession, captureSession.getBookingId().toString(), false);
        createLiveOutput(liveEventName, liveEventName);
        startLiveEvent(liveEventName);
        var liveEvent = checkStreamReady(liveEventName);

        // return ingest url (rtmps)
        return Stream.ofNullable(liveEvent.input().endpoints())
            .flatMap(Collection::stream)
            .filter(e -> e.protocol().equals("RTMP") && e.url().startsWith("rtmps://"))
            .findFirst()
            .map(LiveEventEndpoint::url)
            .orElseThrow(
                () -> new UnknownServerException("Unable to get ingest URL from AMS. No error of exception thrown")
            );
    }

    private void startLiveEvent(String liveEventName) {
        try {
            amsClient.getLiveEvents().start(resourceGroup, accountName, liveEventName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Live Event: " + liveEventName);
            }
            throw e;
        }
    }

    private void stopAndDeleteLiveEvent(String liveEventName) {
        try {
            amsClient
                .getLiveEvents()
                .stop(
                    resourceGroup,
                    accountName,
                    liveEventName,
                    new LiveEventActionInput()
                        .withRemoveOutputsOnStop(true));
            amsClient.getLiveEvents().delete(resourceGroup, accountName, liveEventName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new AMSLiveEventNotFoundException(liveEventName);
            }
            throw e;
        }
    }

    private void stopAndDeleteStreamingEndpoint(String endpointName) {
        try {
            amsClient.getStreamingEndpoints().stop(resourceGroup, accountName, endpointName);
            amsClient.getStreamingEndpoints().delete(resourceGroup, accountName, endpointName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                // live event was not live-streamed- ignore
                return;
            }
            throw e;
        }
    }

    private void deleteStreamingLocator(String locatorName) {
        try {
            amsClient.getStreamingLocators().delete(resourceGroup, accountName, locatorName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                // live event was not live-streamed- ignore error
                return;
            }
            throw e;
        }
    }

    private void deleteLiveOutput(String liveEventName, String liveOutputName) {
        try {
            amsClient.getLiveOutputs().delete(resourceGroup, accountName, liveEventName, liveOutputName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                // live output already deleted with the live event - ignore error
                return;
            }
            throw e;
        }
    }

    private void encodeToMp4(String inputAssetName, String outputAssetName) {
        try {
            amsClient
                .getJobs()
                .create(
                    resourceGroup,
                    accountName,
                    ENCODE_TO_MP4_TRANSFORM,
                    inputAssetName,
                    new JobInner()
                        .withInput(
                            new JobInputAsset()
                                .withAssetName(inputAssetName))
                        .withOutputs(List.of(
                            new JobOutputAsset()
                                .withAssetName(outputAssetName)
                        ))
                );
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        }
    }

    private void checkEncodeComplete(String jobName) throws InterruptedException {
        JobInner job = null;
        do {
            if (job != null) {
                TimeUnit.MILLISECONDS.sleep(10000); // wait 10 seconds
            }
            job = amsClient.getJobs().get(resourceGroup, accountName, ENCODE_TO_MP4_TRANSFORM, jobName);
        } while (!job.state().equals(JobState.FINISHED));
    }

    private LiveEventInner checkStreamReady(String liveEventName) throws InterruptedException {
        LiveEventInner liveEvent;
        do {
            TimeUnit.MILLISECONDS.sleep(2000); // wait 2 seconds
            liveEvent = getLiveEventAms(liveEventName);
        } while (liveEvent == null || !liveEvent.resourceState().equals(LiveEventResourceState.RUNNING));
        return liveEvent;
    }

    private LiveEventInner getLiveEventAms(String liveEventName) {
        try {
            return amsClient.getLiveEvents().get(resourceGroup, accountName, liveEventName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Live event: " + liveEventName);
            }
            throw e;
        }
    }

    private void createLiveOutput(String liveEventName, String liveOutputName) {
        try {
            amsClient.getLiveOutputs().create(
                resourceGroup,
                accountName,
                liveEventName,
                liveOutputName,
                new LiveOutputInner()
                    .withDescription("Live output for: " + liveEventName)
                    .withAssetName(liveEventName)
                    .withArchiveWindowLength(Duration.ofHours(8))
                    .withHls(new Hls().withFragmentsPerTsSegment(5))
                    .withManifestName(liveEventName)
            );
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Live Event: " + liveEventName);
            }
            if (e.getResponse().getStatusCode() == 409) {
                throw new ConflictException("Live Output: " + liveOutputName);
            }
            throw e;
        }
    }

    private void createAsset(String assetName,
                             CaptureSessionDTO captureSession,
                             String containerName,
                             boolean isFinal) {
        try {
            amsClient
                .getAssets()
                .createOrUpdate(
                    resourceGroup,
                    accountName,
                    assetName,
                    new AssetInner()
                        .withContainer(containerName)
                        .withStorageAccountName(isFinal ? finalStorageAccount : ingestStorageAccount)
                        .withDescription(captureSession.getBookingId().toString())
                );
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 400
                && e.getMessage().contains("The storage container is used by another asset already")) {
                throw new ConflictException("Asset: " + assetName);
            }
            if (e.getResponse().getStatusCode() == 409) {
                throw new ConflictException("Asset: " + assetName);
            }
            throw e;
        }
    }

    private void createLiveEvent(CaptureSessionDTO captureSession) {
        var accessToken = UUID.randomUUID();
        try {
            amsClient.getLiveEvents().create(
                resourceGroup,
                accountName,
                getSanitisedLiveEventId(captureSession.getId()),
                new LiveEventInner()
                    .withLocation(LOCATION)
                    .withTags(Map.of(
                        "environment", environmentTag,
                        "application", "pre-recorded evidence",
                        "businessArea", "cross-cutting",
                        "builtFrom", "pre-api"
                    ))
                    .withDescription(captureSession.getBookingId().toString())
                    .withUseStaticHostname(true)
                    .withInput(
                        new LiveEventInput()
                            .withStreamingProtocol(LiveEventInputProtocol.RTMP)
                            .withKeyFrameIntervalDuration("PT6S")
                            .withAccessToken(accessToken.toString())
                            .withAccessControl(
                                new LiveEventInputAccessControl()
                                    .withIp(new IpAccessControl()
                                                .withAllow(List.of(new IpRange()
                                                                       .withName("AllowAll")
                                                                       .withAddress("0.0.0.0")
                                                                       .withSubnetPrefixLength(0)))
                                    )))
                    .withPreview(
                        new LiveEventPreview()
                            .withAccessControl(
                                new LiveEventPreviewAccessControl()
                                    .withIp(new IpAccessControl()
                                                .withAllow(List.of(new IpRange()
                                                                       .withName("AllowAll")
                                                                       .withAddress("0.0.0.0")
                                                                       .withSubnetPrefixLength(0)
                                                )))
                            )));
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure.  " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 409) {
                return;
            }
            throw e;
        }
    }
}
