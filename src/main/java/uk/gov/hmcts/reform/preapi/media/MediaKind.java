package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncoding;
import com.azure.resourcemanager.mediaservices.models.LiveEventEncodingType;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MediaKind implements IMediaService {
    private final String ingestStorageAccount;
    private final String environmentTag;

    private final MediaKindClient mediaKindClient;
    private final CaptureSessionRepository captureSessionRepository;
    private final UserRepository userRepository;

    private static final String LOCATION = "uksouth";

    @Autowired
    public MediaKind(
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${azure.environmentTag}") String env,
        MediaKindClient mediaKindClient,
        CaptureSessionRepository captureSessionRepository,
        UserRepository userRepository
    ) {
        this.ingestStorageAccount = ingestStorageAccount;
        this.environmentTag = env;
        this.mediaKindClient = mediaKindClient;
        this.captureSessionRepository = captureSessionRepository;
        this.userRepository = userRepository;
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

    public MkLiveEvent getLiveEvent(String liveEventName) {
        try {
            return mediaKindClient.getLiveEvent(liveEventName);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live Event: " + liveEventName);
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    @Override
    public CaptureSessionDTO startLiveEvent(UUID captureSessionId) {
        var captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(captureSessionId)
            .orElseThrow(() ->  new NotFoundException("Capture Session: " + captureSessionId));

        if (captureSession.getFinishedAt() != null
            || (captureSession.getStartedAt() != null && captureSession.getIngestAddress() != null)) {
            return new CaptureSessionDTO(captureSession);
        }
        var userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();
        var user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User: " + userId));

        captureSession.setStartedByUser(user);
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionRepository.saveAndFlush(captureSession);

        try {

            //            var liveEventName = uuidToNameString(captureSessionId);
            var liveEventName = "lucastestevent";

            // create live event
            // todo
            createLiveEvent(null);

            // check live event exists
            getLiveEvent(liveEventName);

            // create asset todo add capture session
            createAsset(liveEventName, null);

            // create live output
            createLiveOutput(liveEventName, liveEventName);

            // start live event
            startLiveEvent(liveEventName);

            // check stream ready
            var liveEvent = checkStreamReady(liveEventName);

            // todo get rtmps
            var inputRtmp = Stream.ofNullable(liveEvent.getProperties().getInput().endpoints())
                .flatMap(Collection::stream)
                //  .filter(e -> e.protocol().equals("RTMP") && e.url().startsWith("rtmps://"))
                .findFirst()
                .map(LiveEventEndpoint::url)
                .orElse(null);

            // update capture session
            captureSession.setStatus(RecordingStatus.STANDBY);
            captureSession.setIngestAddress(inputRtmp);
            captureSessionRepository.saveAndFlush(captureSession);
            return new CaptureSessionDTO(captureSession);
        } catch (InterruptedException e) {
            throw new UnknownServerException("Something went wrong when attempting to communicate with Azure");
        } catch (Exception e) {
            captureSession.setStatus(RecordingStatus.FAILURE);
            captureSessionRepository.saveAndFlush(captureSession);
            throw e;
        }
    }

    private void startLiveEvent(String liveEventName) {
        try {
            mediaKindClient.startLiveEvent(liveEventName);
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    private MkStreamingLocator createStreamingLocator(String locatorName, String assetName) {
        try {
            return mediaKindClient.putStreamingLocator(
                locatorName, MkStreamingLocator.builder()
                        .properties(MkStreamingLocator.MkStreamingLocatorProperties.builder()
                                        .assetName(assetName)
                                        .streamingPolicyName("Predefined_ClearStreamingOnly")
                                        .build())
                    .build()
            );
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    private MkLiveEvent checkStreamReady(String liveEventName) throws InterruptedException {
        MkLiveEvent liveEvent;
        do {
            Thread.sleep(2000); // 2 secs
            liveEvent = getLiveEvent(liveEventName);
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

    private void createAsset(String assetName, CaptureSession captureSession) {
        try {
            mediaKindClient.putAsset(
                assetName,
                MkAsset.builder()
                    .properties(MkAssetProperties.builder()
                                    // .container(captureSession.getBooking().getId().toString())
                                    .container("lucastestevent")
                                    .storageAccountName(ingestStorageAccount)
                                    //  .description(captureSession.getBooking().getId().toString())
                                    .description("this is a test")
                                    .build())
                    .build()
            );
        } catch (FeignException.Conflict e) {
            throw new ConflictException("Asset: "  + assetName);
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    private void createLiveEvent(CaptureSession captureSession) {
        var accessToken = UUID.randomUUID();
        try {
            mediaKindClient.putLiveEvent(
                uuidToNameString(captureSession.getId()),
                //                "lucastestevent",
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
                                                  .withEncodingType(LiveEventEncodingType.PASSTHROUGH_BASIC)
                                    )
                                .description(captureSession.getBooking().getId().toString())
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
        } catch (FeignException e) {
            e.printStackTrace();
            throw new MediaKindException();
        }
    }


    /*
    @Override
    public String playLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String stopLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLiveEvents() {
        throw new UnsupportedOperationException();
    }
     */

    private String uuidToNameString(UUID id) {
        return id.toString().replaceAll("-", "");
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
}
