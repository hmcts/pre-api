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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkCreateStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorPaths;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;

import java.time.Instant;
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
    private final String environmentTag;

    private final MediaKindClient mediaKindClient;

    private static final String LOCATION = "uksouth";

    @Autowired
    public MediaKind(
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${platform-env}") String env,
        MediaKindClient mediaKindClient
    ) {
        this.ingestStorageAccount = ingestStorageAccount;
        this.environmentTag = env;
        this.mediaKindClient = mediaKindClient;
    }

    @Override
    public PlaybackDTO playAsset(String assetName) {
        if (getAsset(assetName) == null) {
            throw new NotFoundException("Asset: " + assetName);
        }

        var locators = mediaKindClient.getAssetStreamingLocators(assetName);
        var userId = ((UserAuthentication) SecurityContextHolder.getContext()
            .getAuthentication()).getUserId().toString();

        var locator = locators.getStreamingLocators()
            .stream()
            .filter(l -> l.getName().equals(userId))
            .findFirst()
            .orElse(null);

        if (locator == null) {
            var properties = MkCreateStreamingLocator.MkCreateStreamingLocatorProperties.builder()
                .assetName(assetName)
                .streamingPolicyName("Predefined_ClearStreamingOnly")
                .endTime(Instant.now().plusSeconds(3600).toString())
                .build();
            try {
                mediaKindClient.putStreamingLocator(userId, MkCreateStreamingLocator.builder()
                    .properties(properties).build());
            } catch (FeignException e) {
                throw new MediaKindException();
            }
        }

        // TODO check streaming endpoint running + start if not
        String hostName;
        try {
            hostName = mediaKindClient.getStreamingEndpointByName("default").getProperties().getHostName();
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Streaming Endpoint: default");
        } catch (FeignException e) {
            throw new MediaKindException();
        }

        MkStreamingLocatorPaths paths;
        try {
            paths = mediaKindClient.getStreamingLocatorPaths(userId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Streaming Locator: " + userId);
        } catch (FeignException e) {
            throw new MediaKindException();
        }

        var dash = paths.getStreamingPaths().stream().filter(p -> p.getStreamingProtocol().equals("Dash"))
            .findFirst().orElse(null);
        var hls = paths.getStreamingPaths().stream().filter(p -> p.getStreamingProtocol().equals("Hls"))
            .findFirst().orElse(null);

        return new PlaybackDTO(
            dash != null ? hostName + dash.getPaths().getFirst() : null,
            hls != null ? hostName + hls.getPaths().getFirst() : null,
            ""
        );
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
    public String startLiveEvent(CaptureSessionDTO captureSession) throws InterruptedException {

        var liveEventName = uuidToNameString(captureSession.getId());
        createLiveEvent(captureSession);
        getLiveEventMk(liveEventName);
        createAsset(liveEventName, captureSession);
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

    private void createAsset(String assetName, CaptureSessionDTO captureSession) {
        try {
            mediaKindClient.putAsset(
                assetName,
                MkAsset.builder()
                    .properties(MkAssetProperties.builder()
                                     .container(captureSession.getBookingId().toString())
                                    .storageAccountName(ingestStorageAccount)
                                    .description(captureSession.getBookingId().toString())
                                    .build())
                    .build()
            );
        } catch (FeignException.Conflict e) {
            throw new ConflictException("Asset: "  + assetName);
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    private void createLiveEvent(CaptureSessionDTO captureSession) {
        var accessToken = UUID.randomUUID();
        try {
            mediaKindClient.putLiveEvent(
                uuidToNameString(captureSession.getId()),
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

    private String uuidToNameString(UUID id) {
        return id.toString().replace("-", "");
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
