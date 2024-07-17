package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncoding;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncodingType;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkBuiltInAssetConverterPreset;
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
import uk.gov.hmcts.reform.preapi.media.dto.MkTransform;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransformOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransformProperties;
import uk.gov.hmcts.reform.preapi.media.dto.Tier;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;


@Service
@Slf4j
public class MediaKind implements IMediaService {
    private final String ingestStorageAccount;
    private final String finalStorageAccount;
    private final String environmentTag;
    private final String subscription;

    private final MediaKindClient mediaKindClient;
    private final AzureFinalStorageService azureFinalStorageService;

    private static final String LOCATION = "uksouth";
    private static final String ENCODE_TO_MP4_TRANSFORM = "EncodeToMp4";

    @Autowired
    public MediaKind(
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${azure.finalStorage.accountName}") String finalStorageAccount,
        @Value("${platform-env}") String env,
        @Value("${mediakind.subscription}") String subscription,
        MediaKindClient mediaKindClient,
        AzureFinalStorageService azureFinalStorageService
    ) {
        this.ingestStorageAccount = ingestStorageAccount;
        this.finalStorageAccount = finalStorageAccount;
        this.environmentTag = env;
        this.subscription = subscription;
        this.mediaKindClient = mediaKindClient;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateAssetResponseDTO importAsset(GenerateAssetDTO assetPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        try {
            return new AssetDTO(mediaKindClient.getAsset(assetName));
        } catch (FeignException.NotFound e) {
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
    public String playLiveEvent(UUID liveEventId) {

        assertLiveEventExists(liveEventId);
        assertStreamingEndpointExists(liveEventId);
        try {
            mediaKindClient.startStreamingEndpoint(getShortenedLiveEventId(liveEventId));
        } catch (Exception ex) {
            log.error("Error starting streaming endpoint: " + ex.getMessage());
            throw ex;
        }

        assertStreamingLocatorExists(liveEventId);
        var paths = mediaKindClient.listStreamingLocatorPaths(getSanitisedLiveEventId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(liveEventId, paths);
    }

    public LiveEventDTO getLiveEvent(String liveEventName) {
        return new LiveEventDTO(getLiveEventMk(liveEventName));
    }

    private MkLiveEvent getLiveEventMk(String liveEventName) {
        try {
            return mediaKindClient.getLiveEvent(liveEventName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        }
    }

    public List<LiveEventDTO> getLiveEvents() {
        return getAllMkList(mediaKindClient::getLiveEvents)
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
        encodeToMp4(captureSessionNoHyphen, recordingAssetName);
        waitEncodeComplete(captureSessionNoHyphen);
        var status = azureFinalStorageService.doesIsmFileExist(recordingId.toString())
            ? RecordingStatus.RECORDING_AVAILABLE
            : RecordingStatus.NO_RECORDING;

        mediaKindClient.deleteLiveOutput(captureSessionNoHyphen, captureSessionNoHyphen);
        stopAndDeleteLiveEvent(captureSessionNoHyphen);
        var captureSessionShort = getShortenedLiveEventId(captureSession.getId());
        stopAndDeleteStreamingEndpoint(captureSessionShort);

        // delete returns 204 if not found (no need to catch)
        mediaKindClient.deleteStreamingLocator(captureSessionNoHyphen);

        return status;
    }

    @Override
    public void startLiveEvent(CaptureSessionDTO captureSession) {
        var liveEventName = getSanitisedId(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventMk(liveEventName);
        createAsset(liveEventName, captureSession, captureSession.getBookingId().toString(), false);
        createLiveOutput(liveEventName, liveEventName);
        startLiveEvent(liveEventName);
    }

    private void startLiveEvent(String liveEventName) {
        try {
            mediaKindClient.startLiveEvent(liveEventName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        }
    }

    private void stopAndDeleteLiveEvent(String liveEventName) {
        try {
            mediaKindClient.stopLiveEvent(liveEventName);
        } catch (NotFoundException e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        }
        mediaKindClient.deleteLiveEvent(liveEventName);
    }

    private void stopAndDeleteStreamingEndpoint(String endpointName) {
        try {
            mediaKindClient.stopStreamingEndpoint(endpointName);
        } catch (NotFoundException e) {
            // ignore
            return;
        }
        mediaKindClient.deleteStreamingEndpoint(endpointName);
    }

    private void assertEncodeToMp4TransformExists() {
        try {
            mediaKindClient.getTransform(ENCODE_TO_MP4_TRANSFORM);
        } catch (FeignException.NotFound e) {
            // create EncodeToMp4 transform if it doesn't exist yet
            mediaKindClient.putTransform(
                ENCODE_TO_MP4_TRANSFORM,
                MkTransform.builder()
                    .properties(
                        MkTransformProperties.builder()
                            .outputs(List.of(
                                MkTransformOutput.builder()
                                    .preset(MkBuiltInAssetConverterPreset.builder()
                                                .presetName(MkBuiltInAssetConverterPreset
                                                                .MkAssetConverterPreset
                                                                .CopyAllBitrateInterleaved)
                                                .build())
                                    .relativePriority(MkTransformOutput.MkTransformPriority.Normal)
                                    .build()
                            ))
                            .build()
                    )
                    .build()
            );
        }
    }

    private void encodeToMp4(String inputAssetName, String outputAssetName) {
        assertEncodeToMp4TransformExists();
        mediaKindClient.putJob(
            ENCODE_TO_MP4_TRANSFORM,
            inputAssetName,
            MkJob.builder()
                .name(inputAssetName)
                .properties(MkJob.MkJobProperties.builder()
                                .input(new JobInputAsset()
                                           .withAssetName(inputAssetName)
                                           .withFiles(List.of("")))
                                .outputs(List.of(new JobOutputAsset()
                                                     .withAssetName(outputAssetName)))
                                .build())
                .build());
    }

    private void waitEncodeComplete(String jobName) throws InterruptedException {
        MkJob job = null;
        do {
            if (job != null) {
                TimeUnit.MILLISECONDS.sleep(10000);
            }
            job = mediaKindClient.getJob(ENCODE_TO_MP4_TRANSFORM, jobName);
        } while (!job.getProperties().getState().equals(JobState.FINISHED)
            && !job.getProperties().getState().equals(JobState.ERROR));
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
        } catch (FeignException.Conflict e) {
            throw new ConflictException("Live Output: " + liveOutputName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        }
    }

    private void createAsset(String assetName,
                             CaptureSessionDTO captureSession,
                             String containerName,
                             boolean isFinal) {
        try {
            mediaKindClient.putAsset(
                assetName,
                MkAsset.builder()
                    .properties(MkAssetProperties.builder()
                                    .container(containerName)
                                    .storageAccountName(isFinal ? finalStorageAccount : ingestStorageAccount)
                                    .description(captureSession.getBookingId().toString())
                                    .build())
                    .build()
            );
        } catch (FeignException.Conflict e) {
            throw new ConflictException("Asset: " + assetName);
        }
    }

    private void createLiveEvent(CaptureSessionDTO captureSession) {
        var accessToken = UUID.randomUUID();
        try {
            mediaKindClient.putLiveEvent(
                getSanitisedId(captureSession.getId()),
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
                                                      .withEncodingType(LiveEventEncodingType.STANDARD)
                                        )
                                        .description(captureSession.getBookingId().toString())
                                        .useStaticHostname(true)
                                        .input(new LiveEventInput()
                                                   .withStreamingProtocol(LiveEventInputProtocol.RTMP)
                                                   .withKeyFrameIntervalDuration("PT6S")
                                                   .withAccessToken(accessToken.toString())
                                                   .withAccessControl(
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
        } catch (FeignException.Conflict e) {
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

    @FunctionalInterface
    protected interface GetListFunction<E> {
        MkGetListResponse<E> get(int skip);
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

    private void assertStreamingEndpointExists(UUID liveEventId) {
        var streamingEndpointName = getShortenedLiveEventId(liveEventId);
        var streamingEndpointBody = MkStreamingEndpoint.builder()
                                       .location(LOCATION)
                                       .tags(
                                           Map.of("environment", this.environmentTag,
                                                    "application", "pre-recorded evidence"
                                           )
                                       )
                                       .properties(MkStreamingEndpointProperties.builder()
                                                                                .description(
                                                                                    "Streaming Endpoint for "
                                                                                    + streamingEndpointName
                                                                                )
                                                                                .scaleUnits(0)
                                                                                .sku(
                                                                                    MkStreamingEndpointSku
                                                                                        .builder()
                                                                                        .name(Tier.Standard)
                                                                                        .build()
                                                                                )
                                                                                .build()
                                       )
                                       .build();
        try {
            mediaKindClient.createStreamingEndpoint(streamingEndpointName, streamingEndpointBody);
        } catch (ConflictException e) {
            log.info("Streaming endpoint already exists");
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {

        try {
            log.info("Creating Streaming locator");
            var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);

            mediaKindClient.createStreamingLocator(
                sanitisedLiveEventId,
                MkStreamingLocator.builder()
                                  .properties(MkStreamingLocatorProperties.builder()
                                                                          .assetName(sanitisedLiveEventId)
                                                                          .streamingLocatorId(sanitisedLiveEventId)
                                                                          .streamingPolicyName(
                                                                              "Predefined_ClearStreamingOnly")
                                                                          .build()
                                  ).build()
            );
        } catch (ConflictException e) {
            log.info("Streaming locator already exists");
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(UUID liveEventId, MkStreamingLocatorUrlPaths paths) {
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
                    .map(p -> "https://" + getHostname(liveEventId) + p)
                    .orElseThrow(() -> new RuntimeException("No valid paths returned from Streaming Locator"));
    }

    private String getHostname(UUID liveEventId) {
        return "ep-"
               + getShortenedLiveEventId(liveEventId)
               + "-"
               + subscription
               + "."
               + LOCATION
               + ".streaming.mediakind.com";
    }

    private String getSanitisedId(UUID id) {
        return id.toString().replace("-", "");
    }

    private String getShortenedLiveEventId(UUID liveEventId) {
        return getSanitisedId(liveEventId).substring(0, 23);
    }
}
