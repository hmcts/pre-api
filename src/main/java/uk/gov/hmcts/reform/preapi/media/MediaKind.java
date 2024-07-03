package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncoding;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncodingType;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import feign.FeignException;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkBuiltInAssetConverterPreset;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransform;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransformOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkTransformProperties;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MediaKind implements IMediaService {
    private final String ingestStorageAccount;
    private final String finalStorageAccount;
    private final String environmentTag;

    private final MediaKindClient mediaKindClient;
    private final AzureFinalStorageService azureFinalStorageService;

    private static final String LOCATION = "uksouth";
    private static final String ENCODE_TO_MP4_TRANSFORM = "EncodeToMp4";

    @Autowired
    public MediaKind(
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${azure.finalStorage.accountName}") String finalStorageAccount,
        @Value("${platform-env}") String env,
        MediaKindClient mediaKindClient,
        AzureFinalStorageService azureFinalStorageService
    ) {
        this.ingestStorageAccount = ingestStorageAccount;
        this.finalStorageAccount = finalStorageAccount;
        this.environmentTag = env;
        this.mediaKindClient = mediaKindClient;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String playLiveEvent(@NotNull UUID liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        try {
            return new AssetDTO(mediaKindClient.getAsset(assetName));
        } catch (FeignException.NotFound e) {
            return null;
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    @Override
    public List<AssetDTO> getAssets() {
        try {
            return getAllMkList(mediaKindClient::getAssets)
                .map(AssetDTO::new)
                .collect(Collectors.toList());
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    @Override
    public LiveEventDTO getLiveEvent(String liveEventName) {
        return new LiveEventDTO(getLiveEventMk(liveEventName));
    }

    private MkLiveEvent getLiveEventMk(String liveEventName) {
        try {
            return mediaKindClient.getLiveEvent(liveEventName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        } catch (FeignException e) {
            throw new MediaKindException();
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
        encodeToMp4(captureSessionNoHyphen, recordingNoHyphen);
        waitEncodeComplete(captureSessionNoHyphen);
        var status = azureFinalStorageService.doesIsmFileExist(recordingId.toString())
            ? RecordingStatus.RECORDING_AVAILABLE
            : RecordingStatus.NO_RECORDING;

        stopAndDeleteLiveEvent(captureSessionNoHyphen);
        var captureSessionShort = getShortenedLiveEventId(captureSession.getId());
        stopAndDeleteStreamingEndpoint(captureSessionShort);

        // delete returns 204 if not found (no need to catch)
        mediaKindClient.deleteStreamingLocator(captureSessionShort);
        mediaKindClient.deleteLiveOutput(captureSessionNoHyphen, captureSessionNoHyphen);

        return status;
    }

    @Override
    public String startLiveEvent(CaptureSessionDTO captureSession) throws InterruptedException {
        var liveEventName = getSanitisedId(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventMk(liveEventName);
        createAsset(liveEventName, captureSession, captureSession.getBookingId().toString(), false);
        createLiveOutput(liveEventName, liveEventName);
        startLiveEvent(liveEventName);
        var liveEvent = checkStreamReady(liveEventName);

        // todo return rtmps from mk (uncomment filter)
        return Stream.ofNullable(liveEvent.getProperties().getInput().endpoints())
            .flatMap(Collection::stream)
            //  .filter(e -> e.protocol().equals("RTMP") && e.url().startsWith("rtmps://"))
            .findFirst()
            .map(LiveEventEndpoint::url)
            .orElse(null);
    }

    private void startLiveEvent(String liveEventName) {
        try {
            mediaKindClient.startLiveEvent(liveEventName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    private void stopAndDeleteLiveEvent(String liveEventName) {
        try {
            mediaKindClient.stopLiveEvent(liveEventName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        }
        mediaKindClient.deleteLiveEvent(liveEventName);
    }

    private void stopAndDeleteStreamingEndpoint(String endpointName) {
        try {
            mediaKindClient.stopStreamingEndpoint(endpointName);
        } catch (FeignException.NotFound e) {
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

    private MkLiveEvent checkStreamReady(String liveEventName) throws InterruptedException {
        MkLiveEvent liveEvent;
        do {
            TimeUnit.MILLISECONDS.sleep(2000); // wait 2 seconds
            liveEvent = getLiveEventMk(liveEventName);
        } while (!liveEvent.getProperties().getResourceState().equals("Running"));
        return liveEvent;
    }

    private void createLiveOutput(String liveEventName, String liveOutputName) {
        try {
            mediaKindClient.putLiveOutput(
                liveEventName,
                liveOutputName,
                MkLiveOutput.builder()
                    .properties(MkLiveOutput.MkLiveOutputProperties.builder()
                                    .description("Live output for: " + liveEventName)
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
        } catch (FeignException e) {
            throw new MediaKindException();
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
            throw new ConflictException("Asset: "  + assetName);
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
                    .properties(MkLiveEvent.MkLiveEventProperties.builder()
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
                                                                   .withAllow(List.of(new IpRange()
                                                                                          .withName("AllowAll")
                                                                                          .withAddress("0.0.0.0")
                                                                                          .withSubnetPrefixLength(0)))
                                                       )))
                                    .preview(new LiveEventPreview()
                                                 .withAccessControl(
                                                     new LiveEventPreviewAccessControl()
                                                         .withIp(new IpAccessControl()
                                                                     .withAllow(List.of(new IpRange()
                                                                                            .withName("AllowAll")
                                                                                            .withAddress("0.0.0.0")
                                                                                            .withSubnetPrefixLength(0)))
                                                         )))
                                    .build())
                    .build()
            );
        } catch (FeignException.Conflict e) {
            // do nothing
        } catch (FeignException e) {
            throw new MediaKindException();
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

    private String getSanitisedId(UUID id) {
        return id.toString().replace("-", "");
    }

    private String getShortenedLiveEventId(UUID liveEventId) {
        return getSanitisedId(liveEventId).substring(0, 23);
    }
}
