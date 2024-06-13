package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingEndpointInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingLocatorInner;
import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
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
    private final String environmentTag;

    private final AzureMediaServices amsClient;

    private static final String LOCATION = "uksouth";

    @Autowired
    public AzureMediaService(
        @Value("${azure.resource-group}") String resourceGroup,
        @Value("${azure.account-name}") String accountName,
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${platform-env}") String env,
        AzureMediaServices amsClient) {
        this.resourceGroup = resourceGroup;
        this.accountName = accountName;
        this.ingestStorageAccount = ingestStorageAccount;
        this.environmentTag = env;
        this.amsClient = amsClient;
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
        try {
            return amsClient
                .getAssets()
                .list(resourceGroup, accountName)
                .stream()
                .map(AssetDTO::new)
                .toList();
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        }
    }

    @Override
    public String playLiveEvent(@NotNull UUID liveEventId) {
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");
        Logger.getAnonymousLogger().info("Sanitised live event id: " + sanitisedLiveEventId);

        assertLiveEventExists(sanitisedLiveEventId);
        String hostname = null;
        try {
            hostname = getStreamingEndpointHostname(sanitisedLiveEventId);
            amsClient.getStreamingEndpoints().start(resourceGroup, accountName, sanitisedLiveEventId.substring(0, 23));
        } catch (Exception ex) {
            Logger.getAnonymousLogger().info("Error creating streaming endpoint: " + ex.getMessage());
            throw ex;
        }

        try {
            assertStreamingLocatorExists(liveEventId, sanitisedLiveEventId);
        } catch (Exception e) {
            Logger.getAnonymousLogger().info("Error creating streaming locator: " + e.getMessage());
            if (e instanceof ManagementException managementException) {
                if (managementException.getResponse().getStatusCode() == 409) {
                    Logger.getAnonymousLogger().info("Streaming locator already exists");
                } else {
                    throw e;
                }
            } else {
                throw e;
            }
        }

        var paths = amsClient.getStreamingLocators().listPaths(resourceGroup, accountName, sanitisedLiveEventId);

        return parseLiveOutputUrlFromStreamingLocatorPaths(hostname, paths);
    }

    private String getStreamingEndpointHostname(@NotNull String sanitisedLiveEventId) {
        var streamingEndpointName = sanitisedLiveEventId.substring(0, 23);
        Logger.getAnonymousLogger().info("creating streaming endpoint");
        try {
            return createStreamingEndpoint(streamingEndpointName).hostname();
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error creating streaming endpoint: " + e.getMessage());
            throw e;
        }
    }

    private StreamingEndpointInner createStreamingEndpoint(String streamingEndpointName) {
        try {
            return amsClient.getStreamingEndpoints()
                            .create(
                                resourceGroup,
                                accountName,
                                streamingEndpointName,
                                new StreamingEndpointInner()
                                    .withLocation("UK South")
                                    .withTags(Map.of(
                                        "environment", "Staging",
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

    private void assertStreamingLocatorExists(UUID liveEventId, String sanitisedLiveEventId) {

        try {
            var streamingLocator = amsClient.getStreamingLocators()
                                            .get(resourceGroup, accountName, sanitisedLiveEventId);
            if (streamingLocator != null) {
                Logger.getAnonymousLogger().info("Streaming locator already exists");
                return;
            }
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() != 404) {
                throw e;
            }
        }

        Logger.getAnonymousLogger().info("Creating Streaming locator");
        var streamingLocatorProperties = new StreamingLocatorInner()
            .withAssetName(sanitisedLiveEventId)
            .withStreamingPolicyName("Predefined_ClearStreamingOnly")
            .withStreamingLocatorId(liveEventId);

        amsClient.getStreamingLocators().create(resourceGroup,
                                                accountName,
                                                sanitisedLiveEventId,
                                                streamingLocatorProperties);
    }

    private void assertLiveEventExists(String sanitisedLiveEventId) {
        try {
            var liveEvent = amsClient.getLiveEvents().get(resourceGroup, accountName, sanitisedLiveEventId);
            if (!liveEvent.resourceState().equals(LiveEventResourceState.RUNNING)) {
                throw new AMSLiveEventNotRunningException(sanitisedLiveEventId);
            }
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new AMSLiveEventNotFoundException(sanitisedLiveEventId);
            }
            throw e;
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(String streamingEndpointHostname,
                                                               @NotNull ListPathsResponseInner paths) {
        Logger.getAnonymousLogger().info("parsing live output url from streaming locator paths");
        Logger.getAnonymousLogger().info(streamingEndpointHostname);
        paths.streamingPaths().forEach(p -> Logger.getAnonymousLogger().info(p.paths().toString()));
        return paths.streamingPaths().stream()
            .flatMap(path -> path.paths().stream())
            .map(p -> "https://" + streamingEndpointHostname + p)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Unable to create streaming locator"));
    }

    /*
    @Override
    public String startLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }
    @Override
    public String stopLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

     */

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
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #captureSession.id)")
    public String startLiveEvent(CaptureSessionDTO captureSession) throws InterruptedException {
        var liveEventName = uuidToNameString(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventAms(liveEventName);
        createAsset(liveEventName, captureSession);
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

    private void createAsset(String assetName, CaptureSessionDTO captureSession) {
        try {
            amsClient
                .getAssets()
                .createOrUpdate(
                    resourceGroup,
                    accountName,
                    assetName,
                    new AssetInner()
                        .withContainer(captureSession.getBookingId().toString())
                        .withStorageAccountName(ingestStorageAccount)
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
                uuidToNameString(captureSession.getId()),
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

    private String uuidToNameString(UUID id) {
        return id.toString().replace("-", "");
    }
}
