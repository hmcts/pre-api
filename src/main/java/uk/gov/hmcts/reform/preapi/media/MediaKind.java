package uk.gov.hmcts.reform.preapi.media;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyClearKeyConfiguration;
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
import com.azure.resourcemanager.mediaservices.models.LiveEventEncoding;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncodingType;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingPolicyContentKeys;
import com.microsoft.applicationinsights.TelemetryClient;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.AssetFilesNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkBuiltInPreset;
import uk.gov.hmcts.reform.preapi.media.dto.MkContentKeyPolicy;
import uk.gov.hmcts.reform.preapi.media.dto.MkContentKeyPolicyOptions;
import uk.gov.hmcts.reform.preapi.media.dto.MkContentKeyPolicyProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEventProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpoint;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpointProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpointSku;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.EncryptionScheme;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingPolicy;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingPolicyProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransform;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransformOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransformProperties;
import uk.gov.hmcts.reform.preapi.media.dto.Tier;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;

@Slf4j
@Service
public class MediaKind implements IMediaService {
    private final String ingestStorageAccount;
    private final String finalStorageAccount;
    private final String environmentTag;
    private final String subscription;
    private final String issuer;
    private final String symmetricKey;

    private final MediaKindClient mediaKindClient;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;

    private final TelemetryClient telemetryClient = new TelemetryClient();

    public static final String ENCODE_FROM_MP4_TRANSFORM = "EncodeFromMp4";
    public static final String ENCODE_FROM_INGEST_TRANSFORM = "EncodeFromIngest";
    public static final String DEFAULT_VOD_STREAMING_ENDPOINT = "default";
    public static final String DEFAULT_LIVE_STREAMING_ENDPOINT = "default-live";
    private static final String LOCATION = "uksouth";
    private static final String STREAMING_POLICY_CLEAR_KEY = "Predefined_ClearKey";
    private static final String STREAMING_POLICY_CLEAR_STREAMING_ONLY = "Predefined_ClearStreamingOnly";
    private static final String SENT_FOR_ENCODING = "SENT_FOR_ENCODING";
    private static final String AVAILABLE_IN_FINAL_STORAGE = "AVAILABLE_IN_FINAL_STORAGE";

    @Autowired
    public MediaKind(
        @Value("${azure.ingestStorage.accountName}") String ingestStorageAccount,
        @Value("${azure.finalStorage.accountName}") String finalStorageAccount,
        @Value("${platform-env}") String env,
        @Value("${mediakind.subscription}") String subscription,
        @Value("${mediakind.issuer:}") String issuer,
        @Value("${mediakind.symmetricKey:}") String symmetricKey,
        MediaKindClient mediaKindClient,
        AzureIngestStorageService azureIngestStorageService,
        AzureFinalStorageService azureFinalStorageService
    ) {
        this.ingestStorageAccount = ingestStorageAccount;
        this.finalStorageAccount = finalStorageAccount;
        this.environmentTag = env;
        this.subscription = subscription;
        this.issuer = issuer;
        this.symmetricKey = symmetricKey;
        this.mediaKindClient = mediaKindClient;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public PlaybackDTO playAsset(String assetName, String userId) throws InterruptedException {
        if (getAsset(assetName) == null) {
            throw new AssetFilesNotFoundException(assetName);
        }
        // todo check asset has files
        createContentKeyPolicy(userId, symmetricKey);
        assertStreamingPolicyExists(userId);
        var streamingLocatorName = refreshStreamingLocatorForUser(userId, assetName);

        var hostName = "https://" + assertStreamingEndpointExists(DEFAULT_VOD_STREAMING_ENDPOINT).getProperties().getHostName();
        var paths = mediaKindClient.getStreamingLocatorPaths(streamingLocatorName);

        var dash = paths.getStreamingPaths().stream().filter(p -> p.getStreamingProtocol() == StreamingProtocol.Dash)
            .findFirst().map(p -> p.getPaths().getFirst()).orElse(null);
        var hls = paths.getStreamingPaths().stream().filter(p -> p.getStreamingProtocol() == StreamingProtocol.Hls)
            .findFirst().map(p -> p.getPaths().getFirst()).orElse(null);

        if (dash == null && hls == null) {
            throw new NotFoundException("Playback URL");
        }

        return new PlaybackDTO(
            dash != null ? hostName + dash : null,
            hls != null ? hostName + hls : null,
            paths.getDrm().getClearKey().getLicenseAcquisitionUrl(),
            JWT.create()
                .withIssuer(issuer)
                .withAudience(userId)
                .sign(Algorithm.HMAC256(symmetricKey))
        );
    }

    @Override
    public GenerateAssetResponseDTO importAsset(GenerateAssetDTO generateAssetDTO, boolean sourceIsFinalStorage)
        throws InterruptedException {
        createAsset(generateAssetDTO.getTempAsset(),
                    generateAssetDTO.getDescription(),
                    generateAssetDTO.getSourceContainer(),
                    sourceIsFinalStorage);

        createAsset(generateAssetDTO.getFinalAsset(),
                    generateAssetDTO.getDescription(),
                    generateAssetDTO.getDestinationContainer().toString(),
                    true);

        var fileName = (sourceIsFinalStorage ? azureFinalStorageService : azureIngestStorageService)
            .getMp4FileName(generateAssetDTO.getSourceContainer());
        var jobName = encodeFromMp4(generateAssetDTO.getTempAsset(), generateAssetDTO.getFinalAsset(), fileName);

        var jobState = waitEncodeComplete(jobName, ENCODE_FROM_MP4_TRANSFORM);

        return new GenerateAssetResponseDTO(
            generateAssetDTO.getFinalAsset(),
            generateAssetDTO.getDestinationContainer().toString(),
            generateAssetDTO.getDescription(),
            jobState.toString()
        );
    }

    @Override
    public boolean importAsset(RecordingDTO recordingDTO, boolean isFinal) {
        String assetName = recordingDTO.getId().toString().replace("-", "") + (isFinal ? "_output" : "_temp");
        try {
            createAsset(
                assetName,
                recordingDTO.getId().toString(),
                recordingDTO.getId() + (isFinal ? "" : "-input"),
                isFinal
            );
        } catch (Exception e) {
            log.info("Failed creating asset: {}", assetName);
            return false;
        }
        return true;
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        try {
            return new AssetDTO(mediaKindClient.getAsset(assetName));
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Override
    public List<AssetDTO> getAssets() {
        return getAllMkList(mediaKindClient::getAssets)
            .map(AssetDTO::new)
            .collect(Collectors.toList());
    }

    @Override
    public String playLiveEvent(UUID liveEventId) throws InterruptedException {
        assertLiveEventExists(liveEventId);
        assertStreamingEndpointExists(DEFAULT_LIVE_STREAMING_ENDPOINT);

        assertStreamingLocatorExists(liveEventId);
        var paths = mediaKindClient.listStreamingLocatorPaths(getSanitisedLiveEventId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(DEFAULT_LIVE_STREAMING_ENDPOINT, paths);
    }

    @Override
    public LiveEventDTO getLiveEvent(String liveEventName) {
        return new LiveEventDTO(getLiveEventMk(liveEventName));
    }

    private MkLiveEvent getLiveEventMk(String liveEventName) {
        try {
            return mediaKindClient.getLiveEvent(liveEventName);
        } catch (NotFoundException e) {
            throw new NotFoundException(getLiveEventNotFoundExceptionMessage(liveEventName));
        }
    }

    @Override
    public List<LiveEventDTO> getLiveEvents() {
        return getAllMkList(mediaKindClient::getLiveEvents)
            .map(LiveEventDTO::new)
            .toList();
    }

    @Override
    @Transactional(dontRollbackOn = Exception.class)
    public void stopLiveEvent(CaptureSessionDTO captureSession, UUID recordingId) {
        var captureSessionNoHyphen = getSanitisedLiveEventId(captureSession.getId());
        cleanupStoppedLiveEvent(captureSessionNoHyphen);
    }

    @Override
    public void stopLiveEvent(String liveEventId) {
        try {
            stopAndDeleteLiveEvent(liveEventId);
        } catch (NotFoundException e) {
            // ignore
        }
    }

    @Override
    @Transactional(dontRollbackOn = Exception.class)
    public RecordingStatus stopLiveEventAndProcess(CaptureSessionDTO captureSession, UUID recordingId)
        throws InterruptedException {
        var captureSessionNoHyphen = getSanitisedLiveEventId(captureSession.getId());
        cleanupStoppedLiveEvent(captureSessionNoHyphen);

        var jobName = triggerProcessingStep1(captureSession, captureSessionNoHyphen, recordingId);
        if (jobName == null) {
            return RecordingStatus.NO_RECORDING;
        }
        var encodeFromIngestJobState = waitEncodeComplete(jobName, ENCODE_FROM_INGEST_TRANSFORM);

        telemetryClient.trackMetric(SENT_FOR_ENCODING, 1.0);

        if (encodeFromIngestJobState != JobState.FINISHED) {
            return RecordingStatus.FAILURE;
        }

        var jobName2 = triggerProcessingStep2(recordingId, false);
        if (jobName2 == null) {
            return RecordingStatus.FAILURE;
        }
        var encodeFromMp4JobState = waitEncodeComplete(jobName2, ENCODE_FROM_MP4_TRANSFORM);
        if (encodeFromMp4JobState != JobState.FINISHED) {
            return RecordingStatus.FAILURE;
        }

        var recordingStatus = verifyFinalAssetExists(recordingId);
        if (recordingStatus == RecordingStatus.RECORDING_AVAILABLE) {
            telemetryClient.trackMetric(AVAILABLE_IN_FINAL_STORAGE, 1.0);
            azureIngestStorageService.markContainerAsSafeToDelete(captureSession.getBookingId().toString());
            azureIngestStorageService.markContainerAsSafeToDelete(recordingId.toString());
        }

        return recordingStatus;
    }

    @Override
    public void cleanupStoppedLiveEvent(String liveEventId) {
        mediaKindClient.deleteLiveOutput(liveEventId, liveEventId);
        stopAndDeleteLiveEvent(liveEventId);

        // delete returns 204 if not found (no need to catch)
        mediaKindClient.deleteStreamingLocator(liveEventId);
    }

    @Override
    public void deleteAllStreamingLocatorsAndContentKeyPolicies() {

        getAllMkList(mediaKindClient::getStreamingLocators)
            .map(MkStreamingLocator::getName)
            .forEach(locatorName -> {
                try {
                    mediaKindClient.deleteStreamingLocator(locatorName);
                } catch (Exception e) {
                    log.error("Error deleting streaming locator: {}", e.getMessage());
                }
            });

        getAllMkList(mediaKindClient::getContentKeyPolicies)
            .map(MkContentKeyPolicy::getName)
            .forEach(policyName -> {
                try {
                    mediaKindClient.deleteContentKeyPolicy(policyName);
                } catch (Exception e) {
                    log.error("Error deleting content key policy: {}", e.getMessage());
                }
            });
    }

    @Override
    public void startLiveEvent(CaptureSessionDTO captureSession) {
        var liveEventName = getSanitisedLiveEventId(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventMk(liveEventName);

        try {
            createAsset(liveEventName, captureSession, captureSession.getBookingId().toString(), false);
        } catch (ConflictException e) {
            mediaKindClient.deleteLiveEvent(liveEventName);
            throw e;
        }

        createLiveOutput(liveEventName, liveEventName);
        startLiveEvent(liveEventName);
        assertStreamingLocatorExists(captureSession.getId());
    }

    private void startLiveEvent(String liveEventName) {
        try {
            mediaKindClient.startLiveEvent(liveEventName);
        } catch (NotFoundException e) {
            throw new NotFoundException(getLiveEventNotFoundExceptionMessage(liveEventName));
        }
    }

    @Override
    public String triggerProcessingStep1(CaptureSessionDTO captureSession, String captureSessionNoHyphen,
                                         UUID recordingId) {
        if (!azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString())) {
            log.info("No valid asset files found for capture session [{}] in container named [{}]",
                     captureSession.getId(),
                     captureSession.getBookingId().toString()
            );
            azureIngestStorageService.markContainerAsSafeToDelete(captureSession.getBookingId().toString());
            return null;
        }

        var recordingNoHyphen = getSanitisedLiveEventId(recordingId);
        var recordingTempAssetName = recordingNoHyphen + "_temp";
        var recordingAssetName = recordingNoHyphen + "_output";

        createAsset(recordingTempAssetName, captureSession, recordingId.toString(), false);
        createAsset(recordingAssetName, captureSession, recordingId.toString(), true);
        azureIngestStorageService.markContainerAsProcessing(captureSession.getBookingId().toString());
        return encodeFromIngest(captureSessionNoHyphen, recordingTempAssetName);
    }

    @Override
    public String triggerProcessingStep2(UUID recordingId, boolean isImport) {
        String filename = azureIngestStorageService.tryGetMp4FileName(
            recordingId.toString()
                + (isImport ? "-input" : ""));
        if (filename == null) {
            log.error("Output file from {} transform not found", ENCODE_FROM_INGEST_TRANSFORM);
            return null;
        }

        var recordingNoHyphen = getSanitisedLiveEventId(recordingId);
        var recordingTempAssetName = recordingNoHyphen + "_temp";
        var recordingAssetName = recordingNoHyphen + "_output";

        azureIngestStorageService.markContainerAsProcessing(recordingId.toString());
        return encodeFromMp4(recordingTempAssetName, recordingAssetName, filename);
    }

    @Override
    public RecordingStatus verifyFinalAssetExists(UUID recordingId) {
        var recordingAssetName = getSanitisedLiveEventId(recordingId) + "_output";

        if (!azureFinalStorageService.doesIsmFileExist(recordingId.toString())) {
            log.error("Final asset .ism file not found for asset [{}] in container [{}]",
                      recordingAssetName, recordingId);
            return RecordingStatus.FAILURE;
        }
        return RecordingStatus.RECORDING_AVAILABLE;
    }

    @Override
    public RecordingStatus hasJobCompleted(String transformName, String jobName) {
        var job = mediaKindClient.getJob(transformName, jobName);
        return hasJobCompleted(job) && job.getProperties().getState() == JobState.FINISHED
            ? RecordingStatus.RECORDING_AVAILABLE
            : (job.getProperties().getState() == JobState.ERROR || job.getProperties().getState() == JobState.CANCELED
                ? RecordingStatus.FAILURE
                : RecordingStatus.PROCESSING);
    }

    private boolean hasJobCompleted(MkJob job) {
        var state = job.getProperties().getState();
        var jobName = job.getName();

        if (state.equals(JobState.ERROR)) {
            log.error("Job [{}] failed with error [{}]",
                      jobName,
                      job.getProperties().getOutputs().getLast().error().message());
        } else if (state.equals(JobState.CANCELED)) {
            log.error("Job [{}] was cancelled", jobName);
        }

        return state.equals(JobState.FINISHED)
            || state.equals(JobState.ERROR)
            || state.equals(JobState.CANCELED);
    }

    private String refreshStreamingLocatorForUser(String userId, String assetName) {
        var now = OffsetDateTime.now();
        var streamingLocatorName = userId + "_" + assetName;

        // check streaming locator is still valid
        try {
            var locator = mediaKindClient.getStreamingLocator(streamingLocatorName);
            if (locator.getProperties().getEndTime().toInstant().isAfter(now.toInstant())) {
                return streamingLocatorName;
            }
            mediaKindClient.deleteStreamingLocator(streamingLocatorName);
        } catch (NotFoundException e) {
            // ignore
        }

        mediaKindClient.createStreamingLocator(
            streamingLocatorName,
            MkStreamingLocator.builder()
                .properties(
                    MkStreamingLocatorProperties.builder()
                        .assetName(assetName)
                        .streamingPolicyName(STREAMING_POLICY_CLEAR_KEY)
                        .defaultContentKeyPolicyName(userId)
                        // set end time to midnight tonight
                        .endTime(Timestamp.from(
                            now.toLocalDate()
                                .atTime(LocalTime.MAX)
                                .atZone(now.getOffset())
                                .toInstant()
                        ))
                        .build())
                .build()
        );

        return streamingLocatorName;
    }

    private void assertStreamingPolicyExists(String defaultContentKeyPolicy) {
        try {
            mediaKindClient.getStreamingPolicy(MediaKind.STREAMING_POLICY_CLEAR_KEY);
        } catch (NotFoundException e) {
            log.info("Streaming policy {} was not found. Creating streaming policy.",
                     MediaKind.STREAMING_POLICY_CLEAR_KEY
            );
            mediaKindClient.putStreamingPolicy(
                STREAMING_POLICY_CLEAR_KEY,
                MkStreamingPolicy.builder()
                    .properties(
                        MkStreamingPolicyProperties.builder()
                            .defaultContentKeyPolicyName(defaultContentKeyPolicy)
                            .envelopeEncryption(
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
                            .build()
                    )
                    .build()
            );
        }
    }

    private void createContentKeyPolicy(String userId, String key) {
        try {
            mediaKindClient.getContentKeyPolicy(userId);
        } catch (NotFoundException e) {
            mediaKindClient.putContentKeyPolicy(userId, MkContentKeyPolicy.builder()
                .properties(MkContentKeyPolicyProperties.builder()
                                .description("Content key policy for user: " + userId)
                                .options(
                                    List.of(MkContentKeyPolicyOptions.builder()
                                                .name("key")
                                                .restriction(
                                                    new ContentKeyPolicyTokenRestriction()
                                                        .withIssuer(issuer)
                                                        .withAudience(userId)
                                                        .withRestrictionTokenType(
                                                            ContentKeyPolicyRestrictionTokenType.JWT)
                                                        .withPrimaryVerificationKey(
                                                            new ContentKeyPolicySymmetricTokenKey()
                                                                .withKeyValue(key.getBytes())))
                                                .configuration(new ContentKeyPolicyClearKeyConfiguration())
                                                .build()))
                                .build())
                .build());
        }
    }

    private void stopAndDeleteLiveEvent(String liveEventName) {
        try {
            mediaKindClient.stopLiveEvent(liveEventName);
        } catch (NotFoundException e) {
            throw new NotFoundException(getLiveEventNotFoundExceptionMessage(liveEventName));
        } catch (FeignException.BadRequest e) {
            // live output still exists (only occurs on manually created live events)
            log.info("Skipped stopping live event. The live event will be cleaned up by deletion.");
        }
        mediaKindClient.deleteLiveEvent(liveEventName);
    }

    private void assertTransformExists(String transformName) {
        try {
            mediaKindClient.getTransform(transformName);
        } catch (NotFoundException e) {
            // create transform if it doesn't exist yet
            mediaKindClient.putTransform(
                transformName,
                MkTransform.builder()
                    .properties(
                        MkTransformProperties.builder()
                            .outputs(List.of(
                                MkTransformOutput.builder()
                                    .relativePriority(MkTransformOutput.MkTransformPriority.Normal)
                                    .preset(getMkBuiltInPreset(transformName))
                                    .build()
                            ))
                            .build()
                    )
                    .build()
            );
        }
    }

    private MkBuiltInPreset getMkBuiltInPreset(String transformName) {
        return switch(transformName) {
            case ENCODE_FROM_INGEST_TRANSFORM -> MkBuiltInPreset
                .builder()
                .odataType(MkBuiltInPreset.BUILT_IN_PRESET_ASSET_CONVERTER)
                .presetName(MkBuiltInPreset.MkAssetConverterPreset.CopyTopBitrateInterleaved)
                .build();
            case ENCODE_FROM_MP4_TRANSFORM -> MkBuiltInPreset
                .builder()
                .odataType(MkBuiltInPreset.BUILT_IN_PRESET_STANDARD_ENCODER)
                .presetName(MkBuiltInPreset.MkAssetConverterPreset.H264SingleBitrate720p)
                .build();
            default -> throw new IllegalArgumentException(
                "Invalid MediaKind transform name '" + transformName + "'"
            );
        };
    }

    private String encodeFromIngest(String inputAssetName, String outputAssetName) {
        return runEncodeTransform(inputAssetName, outputAssetName, ENCODE_FROM_INGEST_TRANSFORM, "");
    }

    private String encodeFromMp4(String inputAssetName, String outputAssetName, String fileName) {
        return runEncodeTransform(inputAssetName, outputAssetName, ENCODE_FROM_MP4_TRANSFORM, fileName);
    }

    private String runEncodeTransform(String inputAssetName,
                                      String outputAssetName,
                                      String transformName,
                                      String fileName) {
        assertTransformExists(transformName);
        var jobName = inputAssetName + "-" + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        log.info("Creating job [{}]", jobName);
        mediaKindClient.putJob(
            transformName,
            jobName,
            MkJob.builder()
                .name(jobName)
                .properties(MkJob.MkJobProperties.builder()
                                .input(new JobInputAsset()
                                           .withAssetName(inputAssetName)
                                           .withFiles(List.of(fileName)))
                                .outputs(List.of(new JobOutputAsset()
                                                     .withAssetName(outputAssetName)))
                                .build())
                .build());
        log.info("Job [{}] created", jobName);
        return jobName;
    }

    private JobState waitEncodeComplete(String jobName, String transformName) throws InterruptedException {
        log.info("Waiting for job [{}] to complete", jobName);
        MkJob job = null;
        do {
            if (job != null) {
                TimeUnit.MILLISECONDS.sleep(10000);
            }
            job = mediaKindClient.getJob(transformName, jobName);
        } while (!hasJobCompleted(job));
        return job.getProperties().getState();
    }

    private MkStreamingEndpoint checkStreamingEndpointReady(MkStreamingEndpoint endpoint) throws InterruptedException {
        var endpointName = endpoint.getName();
        while (endpoint.getProperties().getResourceState() != MkStreamingEndpointProperties.ResourceState.Running) {
            TimeUnit.MILLISECONDS.sleep(2000); // wait 2 seconds
            endpoint = mediaKindClient.getStreamingEndpointByName(endpointName);
        }
        return endpoint;
    }

    private void createLiveOutput(String liveEventName, String liveOutputName) {
        try {
            mediaKindClient.putLiveOutput(
                liveEventName,
                liveOutputName,
                MkLiveOutput.builder()
                            .properties(MkLiveOutput.MkLiveOutputProperties.builder()
                                                                           .description(
                                                                               "Live output for: " + liveEventName
                                                                           )
                                                                           .assetName(liveEventName)
                                                                           .archiveWindowLength("PT8H")
                                                                           .hls(new Hls().withFragmentsPerTsSegment(5))
                                                                           .manifestName(liveEventName)
                                                                           .build())
                            .build()
            );
        } catch (ConflictException e) {
            throw new ConflictException("Live Output: " + liveOutputName);
        } catch (NotFoundException e) {
            throw new NotFoundException(getLiveEventNotFoundExceptionMessage(liveEventName));
        }
    }

    private void createAsset(String assetName,
                             CaptureSessionDTO captureSession,
                             String containerName,
                             boolean isFinal) {
        createAsset(assetName,
                    captureSession.getBookingId().toString(),
                    containerName,
                    isFinal);
    }

    private void createAsset(String assetName,
                             String description,
                             String containerName,
                             boolean isFinal) {
        try {
            log.info("Creating asset: {} ({})", assetName, description);
            mediaKindClient.putAsset(
                assetName,
                MkAsset.builder()
                    .properties(MkAssetProperties.builder()
                                    .container(containerName)
                                    .storageAccountName(isFinal ? finalStorageAccount : ingestStorageAccount)
                                    .description(description)
                                    .build())
                    .build()
            );
        } catch (ConflictException e) {
            throw new ConflictException("Asset: " + assetName);
        }
    }

    private void createLiveEvent(CaptureSessionDTO captureSession) {
        var accessToken = UUID.randomUUID();
        try {
            mediaKindClient.putLiveEvent(
                getSanitisedLiveEventId(captureSession.getId()),
                MkLiveEvent.builder()
                           .location(LOCATION)
                           .tags(Map.of(
                               "environment", environmentTag,
                               "application", "pre-recorded evidence",
                               "businessArea", "cross-cutting",
                               "builtFrom", "pre-api"
                           ))
                           .properties(MkLiveEventProperties.builder()
                                        .encoding(new LiveEventEncoding()
                                                      .withEncodingType(LiveEventEncodingType.PASSTHROUGH_BASIC)
                                        )
                                        .description(captureSession.getBookingId().toString())
                                        .useStaticHostname(true)
                                        .input(MkLiveEventProperties.MkLiveEventInput.builder()
                                                   .streamingProtocol(MkLiveEventProperties.StreamingProtocol.RTMPS)
                                                   .keyFrameIntervalDuration("PT6S")
                                                   .accessToken(accessToken.toString())
                                                   .accessControl(
                                                       new LiveEventInputAccessControl()
                                                           .withIp(new IpAccessControl()
                                                                       .withAllow(
                                                                           List.of(new IpRange()
                                                                                       .withName("AllowAll")
                                                                                       .withAddress("0.0.0.0")
                                                                                       .withSubnetPrefixLength(0)
                                                                           )
                                                                       )
                                                           )
                                                   )
                                                   .build()
                                        )
                                        .preview(new LiveEventPreview()
                                                     .withAccessControl(
                                                         new LiveEventPreviewAccessControl()
                                                             .withIp(new IpAccessControl()
                                                                         .withAllow(
                                                                             List.of(new IpRange()
                                                                                         .withName("AllowAll")
                                                                                         .withAddress("0.0.0.0")
                                                                                         .withSubnetPrefixLength(0)
                                                                             )
                                                                         )
                                                             )
                                                     )
                                        )
                                        .build())
                           .build()
            );
        } catch (ConflictException e) {
            log.info("Live Event already exists. Continuing...");
        }
    }

    protected <E> Stream<E> getAllMkList(GetListFunction<E> func) {
        Integer[] skip = {0};

        return Stream.iterate(func.get(skip[0]), Objects::nonNull, res -> {
            if (res.getNextLink() != null) {
                skip[0] = res.getSupplemental().getPagination().getEnd();
                return func.get(skip[0]);
            }
            return null;
        }).map(MkGetListResponse::getValue).flatMap(List::stream);
    }

    private void assertLiveEventExists(UUID liveEventId) {
        var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
        try {
            var liveEvent = mediaKindClient.getLiveEvent(sanitisedLiveEventId);
            if (!liveEvent.getProperties().getResourceState().equals(LiveEventResourceState.RUNNING.toString())) {
                throw new LiveEventNotRunningException(sanitisedLiveEventId);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private MkStreamingEndpoint assertStreamingEndpointExists(String endpointName) throws InterruptedException {
        try {
            var endpoint = mediaKindClient.getStreamingEndpointByName(endpointName);
            if (endpoint.getProperties().getResourceState() != MkStreamingEndpointProperties.ResourceState.Running) {
                mediaKindClient.startStreamingEndpoint(endpointName);
                endpoint = checkStreamingEndpointReady(endpoint);
            }
            return endpoint;
        } catch (NotFoundException e) {
            var endpoint = mediaKindClient.createStreamingEndpoint(
                endpointName,
                MkStreamingEndpoint.builder()
                    .location(LOCATION)
                    .tags(Map.of(
                        "environment", environmentTag,
                        "application", "pre-recorded evidence"))
                    .properties(
                        MkStreamingEndpointProperties.builder()
                            .description("Streaming endpoint: " + endpointName)
                            .scaleUnits(0)
                            .sku(
                                MkStreamingEndpointSku
                                    .builder()
                                    .name(Tier.Standard)
                                    .build()
                            )
                            .build())
                    .build());
            mediaKindClient.startStreamingEndpoint(endpointName);
            return checkStreamingEndpointReady(endpoint);
        }
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {
        var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);

        try {
            mediaKindClient.getStreamingLocator(sanitisedLiveEventId);
        } catch (NotFoundException e) {
            createStreamingLocator(sanitisedLiveEventId);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private void createStreamingLocator(String sanitisedLiveEventId) {
        log.info("Creating Streaming locator");
        try {
            // Streaming Locator for a live event
            mediaKindClient.createStreamingLocator(
                sanitisedLiveEventId,
                MkStreamingLocator.builder()
                    .properties(MkStreamingLocatorProperties
                                    .builder()
                                    .assetName(sanitisedLiveEventId)
                                    .streamingLocatorId(sanitisedLiveEventId)
                                    .streamingPolicyName(STREAMING_POLICY_CLEAR_STREAMING_ONLY)
                                    .build())
                    .build()
            );
        } catch (ConflictException e) {
            log.info("Streaming locator already exists");
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(String endpointName, MkStreamingLocatorUrlPaths paths) {
        log.info("parsing live output url from streaming locator paths");
        paths.getStreamingPaths().forEach(p -> {
            log.info(String.valueOf(p.getEncryptionScheme()));
            log.info(String.valueOf(p.getStreamingProtocol()));
            p.getPaths().forEach(log::info);
        });
        return paths.getStreamingPaths().stream()
                    .filter(p -> p.getEncryptionScheme() == EncryptionScheme.NoEncryption
                        && p.getStreamingProtocol() == StreamingProtocol.Hls)
                    .flatMap(path -> path.getPaths().stream())
                    .findFirst()
                    .map(p -> "https://" + getHostname(endpointName) + p)
                    .orElseThrow(() -> new RuntimeException("No valid paths returned from Streaming Locator"));
    }

    private String getHostname(String endpointName) {
        return "ep-"
               + endpointName
               + "-"
               + subscription
               + "."
               + LOCATION
               + ".streaming.mediakind.com";
    }

    private String getLiveEventNotFoundExceptionMessage(String liveEventName) {
        return "Live Event: " + liveEventName;
    }

    @FunctionalInterface
    protected interface GetListFunction<E> {
        MkGetListResponse<E> get(int skip);
    }
}
