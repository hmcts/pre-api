package uk.gov.hmcts.reform.preapi.media;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ContentKeyPolicyInner;
import com.azure.resourcemanager.mediaservices.fluent.models.JobInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingEndpointInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingLocatorInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingPolicyInner;
import com.azure.resourcemanager.mediaservices.models.ArmStreamingEndpointCurrentSku;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyClearKeyConfiguration;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyOption;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyRestrictionTokenType;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicySymmetricTokenKey;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyTokenRestriction;
import com.azure.resourcemanager.mediaservices.models.DefaultKey;
import com.azure.resourcemanager.mediaservices.models.EnabledProtocols;
import com.azure.resourcemanager.mediaservices.models.EnvelopeEncryption;
import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventActionInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpointResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingPolicyContentKeys;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
@Log4j2
public class AzureMediaService implements IMediaService {
    private static final String LOCATION = "uksouth";
    private static final String ENCODE_TO_MP4_TRANSFORM = "EncodeToMP4";
    private static final String STREAMING_POLICY_CLEAR_KEY = "Predefined_ClearKey";
    private static final String DEFAULT_STREAMING_ENDPOINT = "default";

    private final String resourceGroup;
    private final String accountName;
    private final String ingestStorageAccount;
    private final String finalStorageAccount;
    private final String environmentTag;
    private final String issuer;
    private final String symmetricKey;
    private final AzureMediaServices amsClient;
    private final AzureFinalStorageService azureFinalStorageService;

    @Autowired
    public AzureMediaService(
        @Value("${azure.resource-group}") String resourceGroup,
        @Value("${azure.account-name}") String accountName,
        @Value("${azure.ingestStorage.accountName}") String ingestStorageAccount,
        @Value("${azure.finalStorage.accountName}") String finalStorageAccount,
        @Value("${platform-env}") String env,
        @Value("${mediakind.issuer:}") String issuer,
        @Value("${mediakind.symmetricKey:}") String symmetricKey,
        AzureMediaServices amsClient,
        AzureFinalStorageService azureFinalStorageService) {
        this.resourceGroup = resourceGroup;
        this.accountName = accountName;
        this.ingestStorageAccount = ingestStorageAccount;
        this.finalStorageAccount = finalStorageAccount;
        this.environmentTag = env;
        this.issuer = issuer;
        this.symmetricKey = symmetricKey;
        this.amsClient = amsClient;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public PlaybackDTO playAsset(String assetName, String userId) {
        getAsset(assetName);
        // todo check asset has files
        createContentKeyPolicy(userId, Base64.getEncoder().encodeToString(symmetricKey.getBytes()));
        assertStreamingPolicyExists(userId);
        refreshStreamingLocatorForUser(userId, assetName);

        var hostName = "https://" + assertStreamingEndpointExists(DEFAULT_STREAMING_ENDPOINT).hostname();
        var paths = amsClient.getStreamingLocators().listPaths(resourceGroup, accountName, userId);
        var manifest = hostName + paths.streamingPaths().getFirst().paths().getFirst();

        return new PlaybackDTO(
            manifest,
            manifest,
            null,
            JWT.create()
                .withIssuer(issuer)
                .withAudience(userId)
                .withNotBefore(Instant.now().minus(5, ChronoUnit.MINUTES))
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256(symmetricKey))
        );
    }

    private StreamingEndpointInner assertStreamingEndpointExists(String endpointName) {
        try {
            var endpoint = amsClient.getStreamingEndpoints()
                .get(resourceGroup, accountName, endpointName);
            if (endpoint.resourceState() != StreamingEndpointResourceState.RUNNING) {
                amsClient.getStreamingEndpoints()
                    .start(resourceGroup, accountName, endpointName);
                endpoint = amsClient.getStreamingEndpoints()
                    .get(resourceGroup, accountName, endpointName);
            }
            return endpoint;
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure.");
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                var endpoint = amsClient.getStreamingEndpoints()
                    .create(
                        resourceGroup,
                        accountName,
                        DEFAULT_STREAMING_ENDPOINT,
                        new StreamingEndpointInner()
                            .withLocation(LOCATION)
                            .withTags(Map.of(
                                "environment", environmentTag,
                                "application", "pre-recorded evidence"
                            ))
                            .withDescription("Default streaming endpoint")
                            .withScaleUnits(0)
                            // todo fix this
                            .withSku(new ArmStreamingEndpointCurrentSku())
                    );
                amsClient.getStreamingEndpoints()
                    .start(resourceGroup, accountName, endpointName);
                return endpoint;
            }
            throw e;
        }
    }

    private void refreshStreamingLocatorForUser(String userId, String assetName) {
        try {
            amsClient.getStreamingLocators().delete(resourceGroup, accountName, userId);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() != 404) {
                throw e;
            }
        }

        amsClient.getStreamingLocators()
            .create(
                resourceGroup,
                accountName,
                userId,
                new StreamingLocatorInner()
                    .withAssetName(assetName)
                    .withStreamingPolicyName(STREAMING_POLICY_CLEAR_KEY)
                    .withDefaultContentKeyPolicyName(userId)
                    .withEndTime(Instant.now().plusSeconds(3600).atZone(ZoneId.of("UTC")).toOffsetDateTime())
            );
    }

    private void assertStreamingPolicyExists(String defaultContentKeyPolicy) {
        try {
            amsClient.getStreamingPolicies()
                .get(resourceGroup, accountName, STREAMING_POLICY_CLEAR_KEY);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                amsClient.getStreamingPolicies()
                    .create(
                        resourceGroup,
                        accountName,
                        STREAMING_POLICY_CLEAR_KEY,
                        new StreamingPolicyInner()
                            .withDefaultContentKeyPolicyName(defaultContentKeyPolicy)
                            .withEnvelopeEncryption(
                                new EnvelopeEncryption()
                                    .withEnabledProtocols(
                                        new EnabledProtocols()
                                            .withDash(true)
                                            .withHls(true)
                                            .withSmoothStreaming(false)
                                            .withDownload(false))
                                    .withContentKeys(
                                        new StreamingPolicyContentKeys()
                                            .withDefaultKey(new DefaultKey()
                                                                .withLabel("ContentKey_AES")
                                                                .withPolicyName(defaultContentKeyPolicy)))
                            )
                    );
                return;
            }
            throw e;
        }
    }

    private void createContentKeyPolicy(String userId, String key) {
        try {
            amsClient.getContentKeyPolicies()
                .get(resourceGroup, accountName, userId);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                amsClient.getContentKeyPolicies()
                    .createOrUpdate(
                        resourceGroup,
                        accountName,
                        userId,
                        new ContentKeyPolicyInner()
                            .withDescription("Content key policy for user: " + userId)
                            .withOptions(List.of(
                                new ContentKeyPolicyOption()
                                    .withName("key")
                                    .withRestriction(
                                        new ContentKeyPolicyTokenRestriction()
                                            .withIssuer(issuer)
                                            .withAudience(userId)
                                            .withRestrictionTokenType(
                                                ContentKeyPolicyRestrictionTokenType.JWT)
                                            .withPrimaryVerificationKey(
                                                new ContentKeyPolicySymmetricTokenKey()
                                                    .withKeyValue(key.getBytes())))
                                    .withConfiguration(new ContentKeyPolicyClearKeyConfiguration())))
                    );
                return;
            }
            throw e;
        }
    }

    @Override
    public GenerateAssetResponseDTO importAsset(GenerateAssetDTO generateAssetDTO) throws InterruptedException {
        createAsset(
            generateAssetDTO.getTempAsset(),
            generateAssetDTO.getDescription(),
            generateAssetDTO.getSourceContainer(),
            true
        );

        createAsset(
            generateAssetDTO.getFinalAsset(),
            generateAssetDTO.getDescription(),
            generateAssetDTO.getDestinationContainer(),
            true
        );

        var jobName = encodeToMp4(generateAssetDTO.getTempAsset(), generateAssetDTO.getFinalAsset());

        var jobState = checkEncodeComplete(jobName);

        return new GenerateAssetResponseDTO(
            generateAssetDTO.getFinalAsset(),
            generateAssetDTO.getDestinationContainer(),
            generateAssetDTO.getDescription(),
            jobState.toString()
        );
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        try {
            return new AssetDTO(amsClient.getAssets().get(resourceGroup, accountName, assetName));
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
            .listPaths(resourceGroup, accountName, getSanitisedId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(hostname, paths);
    }

    private String getSanitisedId(UUID id) {
        return id.toString().replace("-", "");
    }

    private String getShortenedLiveEventId(UUID liveEventId) {
        return getSanitisedId(liveEventId).substring(0, 23);
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
        log.info("Creating streaming endpoint: {}", streamingEndpointName);
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
                log.info("Streaming endpoint {} already exists", streamingEndpointName);
                return amsClient.getStreamingEndpoints()
                    .get(resourceGroup, accountName, streamingEndpointName);
            }
            throw e;
        }
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {

        try {
            Logger.getAnonymousLogger().info("Creating Streaming locator");
            var sanitisedLiveEventId = getSanitisedId(liveEventId);
            var streamingLocatorProperties = new StreamingLocatorInner()
                .withAssetName(sanitisedLiveEventId)
                .withStreamingPolicyName("Predefined_ClearStreamingOnly")
                .withStreamingLocatorId(liveEventId);

            amsClient.getStreamingLocators().create(
                resourceGroup,
                accountName,
                sanitisedLiveEventId,
                streamingLocatorProperties
            );
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
        var recordingNoHyphen = getSanitisedId(recordingId);
        var recordingAssetName = recordingNoHyphen + "_output";
        var captureSessionNoHyphen = getSanitisedId(captureSession.getId());

        createAsset(recordingAssetName, captureSession, recordingId.toString(), true);
        var jobName = encodeToMp4(captureSessionNoHyphen, recordingAssetName);
        checkEncodeComplete(jobName);

        var status = azureFinalStorageService.doesIsmFileExist(recordingId.toString())
            ? RecordingStatus.RECORDING_AVAILABLE
            : RecordingStatus.NO_RECORDING;

        stopAndDeleteLiveEvent(captureSessionNoHyphen);
        var captureSessionShort = getShortenedLiveEventId(captureSession.getId());
        stopAndDeleteStreamingEndpoint(captureSessionShort);
        deleteStreamingLocator(captureSessionShort);
        deleteLiveOutput(captureSessionNoHyphen, captureSessionNoHyphen);

        return status;
    }

    @Override
    @Transactional(dontRollbackOn = Exception.class)
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #captureSession.id)")
    public void startLiveEvent(CaptureSessionDTO captureSession) {
        var liveEventName = getSanitisedId(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventAms(liveEventName);
        createAsset(liveEventName, captureSession, captureSession.getBookingId().toString(), false);
        createLiveOutput(liveEventName, liveEventName);
        startLiveEvent(liveEventName);
    }

    private void startLiveEvent(String liveEventName) {
        try {
            amsClient.getLiveEvents().beginStart(resourceGroup, accountName, liveEventName);
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
            log.info("Stopping live event: {}", liveEventName);
            amsClient
                .getLiveEvents()
                .stop(
                    resourceGroup,
                    accountName,
                    liveEventName,
                    new LiveEventActionInput()
                        .withRemoveOutputsOnStop(true)
                );
            log.info("Stopped live event: {}", liveEventName);
            log.info("Deleting live event: {}", liveEventName);
            amsClient.getLiveEvents().delete(resourceGroup, accountName, liveEventName);
            log.info("Deleted live event: {}", liveEventName);
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                log.info("Unable to find live event: {}", liveEventName);
                throw new AMSLiveEventNotFoundException(liveEventName);
            }
            throw e;
        }
    }

    private void stopAndDeleteStreamingEndpoint(String endpointName) {
        try {
            log.info("Stopping streaming endpoint: {}", endpointName);
            amsClient.getStreamingEndpoints().stop(resourceGroup, accountName, endpointName);
            log.info("Stopped streaming endpoint: {}", endpointName);
            log.info("Deleting streaming endpoint: {}", endpointName);
            amsClient.getStreamingEndpoints().delete(resourceGroup, accountName, endpointName);
            log.info("Deleted streaming endpoint: {}", endpointName);
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
            log.info("Deleting streaming locator: {}", locatorName);
            amsClient.getStreamingLocators().delete(resourceGroup, accountName, locatorName);
            log.info("Deleted streaming locator: {}", locatorName);
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
            log.info("Deleting live output: {}", liveOutputName);
            amsClient.getLiveOutputs().delete(resourceGroup, accountName, liveEventName, liveOutputName);
            log.info("Deleted live output: {}", liveOutputName);
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

    private String encodeToMp4(String inputAssetName, String outputAssetName) {
        var jobName = inputAssetName + "-" + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        try {
            amsClient
                .getJobs()
                .create(
                    resourceGroup,
                    accountName,
                    ENCODE_TO_MP4_TRANSFORM,
                    jobName,
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

        return jobName;
    }

    private JobState checkEncodeComplete(String jobName) throws InterruptedException {
        JobInner job = null;
        do {
            if (job != null) {
                TimeUnit.MILLISECONDS.sleep(5000); // wait 5 seconds
            }
            job = amsClient.getJobs().get(resourceGroup, accountName, ENCODE_TO_MP4_TRANSFORM, jobName);
        } while (!job.state().equals(JobState.FINISHED)
            && !job.state().equals(JobState.ERROR)
            && !job.state().equals(JobState.CANCELED));

        return job.state();
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
            amsClient.getLiveOutputs().beginCreate(
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
        createAsset(
            assetName,
            captureSession.getBookingId().toString(),
            containerName,
            isFinal
        );
    }

    private void createAsset(String assetName,
                             String description,
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
                        .withDescription(description)
                );
        } catch (IllegalArgumentException e) {
            throw new UnknownServerException("Unable to communicate with Azure. " + e.getMessage());
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 400
                && e.getMessage().contains("The storage container is used by another asset already")) {
                throw new ConflictException("Asset: " + assetName);
            }
            if (e.getResponse().getStatusCode() == 409) {
                log.error(e.getMessage());
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
                getSanitisedId(captureSession.getId()),
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
                            ))
            );
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
