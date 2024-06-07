package uk.gov.hmcts.reform.preapi.media;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
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
    private final MediaKindClient mediaKindClient;
    private final CaptureSessionRepository captureSessionRepository;
    private final UserRepository userRepository;

    @Autowired
    public MediaKind(MediaKindClient mediaKindClient,
                     CaptureSessionRepository captureSessionRepository, UserRepository userRepository) {
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

    @Override
    public CaptureSessionDTO startLiveEvent(UUID captureSessionId) {
//        var captureSession = captureSessionRepository
//            .findByIdAndDeletedAtIsNull(captureSessionId)
//            .orElseThrow(() ->  new NotFoundException("Capture Session: " + captureSessionId));

//        if (captureSession.getFinishedAt() != null
//            || (captureSession.getStartedAt() != null && captureSession.getIngestAddress() != null)) {
//            return new CaptureSessionDTO(captureSession);
//        }

        var liveEventName = uuidToNameString(captureSessionId);

        liveEventName = "lucas-test-event-1";
        createLiveEvent(null);

        try {
            // check live event exists
            mediaKindClient.getLiveEvent(liveEventName);
            // start live event
            mediaKindClient.startLiveEvent(liveEventName);

            MkLiveEvent liveEvent;
            do {
                Thread.sleep(2000);
                liveEvent = mediaKindClient.getLiveEvent(liveEventName);
                System.out.println(liveEvent.getProperties().getResourceState());
            } while (!liveEvent.getProperties().getResourceState().equals("Running"));
            // get live event details


//            var inputRtmp = Stream.ofNullable(liveEvent
//                                                  .getProperties()
//                                                  .getInput()
//                                                  .endpoints())
//                .flatMap(Collection::stream)
//                .filter(e -> e.url().startsWith("rtmps://"))
//                .findFirst()
//                .map(LiveEventEndpoint::url)
//                .orElse(null);

            var inputRtmp = liveEvent.getProperties().getInput().endpoints().stream().findFirst().orElse(null);

            System.out.println("INPUT RTMP:" + inputRtmp);

//            var liveOutputUrl = generateLiveOutputUrl();

//            var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
//            captureSession.setStartedByUser(userRepository.findById(auth.getUserId()).orElse(null));
//            captureSession.setStartedAt(Timestamp.from(Instant.now()));
//            captureSession.setIngestAddress(inputRtmp);
//            captureSession.setLiveOutputUrl(liveOutputUrl);
//            captureSessionRepository.save(captureSession);
            return null;
//            return new CaptureSessionDTO(captureSession);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Capture Session: " + captureSessionId);
        } catch (FeignException | InterruptedException e) {
            throw new MediaKindException();
        }
    }

//    private String generateLiveOutputUrl(String liveEventName) {
//        var appMediaService = "pre-mediakind-test"
//        return "https://" + liveEventName + "_" + appMediaService + "-uksouth1.streaming.media.azure.net/";
//    }

    private void createLiveEvent(CaptureSession captureSession) {
        var accessToken = UUID.randomUUID();
        System.out.println("CREATING LIVE EVENT WITH ACCESS TOKEN: " + accessToken);
        mediaKindClient.putLiveEvent(
//            uuidToNameString(captureSession.getId()),
            "lucas-test-event-1",
            MkLiveEvent.builder()
                .location("uksouth")
                .tags(Map.of(
                    "environment", "development",
                    "application", "pre-recorded evidence",
                    "businessArea", "cross-cutting",
                    "builtFrom", "azurePortal"
                ))
                .properties(MkLiveEvent.MkLiveEventProperties.builder()
                                .encoding(new LiveEventEncoding()
                                              .withEncodingType(LiveEventEncodingType.PASSTHROUGH_BASIC)
                                )
//                                .description(captureSession.getBooking().getId().toString())
                                .description("this is a test")
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
