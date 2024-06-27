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
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
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
import uk.gov.hmcts.reform.preapi.media.dto.Tier;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;
import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getShortenedLiveEventId;


@Service
public class MediaKind implements IMediaService {
    private final String ingestStorageAccount;
    private final String environmentTag;

    private final MediaKindClient mediaKindClient;

    private static final Logger logger = LoggerFactory.getLogger(MediaKind.class);

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
            logger.error("Error starting streaming endpoint: " + ex.getMessage());
            throw ex;
        }

        assertStreamingLocatorExists(liveEventId);
        var paths = mediaKindClient.listStreamingLocatorPaths(getSanitisedLiveEventId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(paths);
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
            throw new ConflictException("Asset: " + assetName);
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
                                                                                           .withAllow(List.of(new IpRange()
                                                                                                                  .withName(
                                                                                                                      "AllowAll")
                                                                                                                  .withAddress(
                                                                                                                      "0.0.0.0")
                                                                                                                  .withSubnetPrefixLength(
                                                                                                                      0)))
                                                                               )))
                                                            .preview(new LiveEventPreview()
                                                                         .withAccessControl(
                                                                             new LiveEventPreviewAccessControl()
                                                                                 .withIp(new IpAccessControl()
                                                                                             .withAllow(List.of(new IpRange()
                                                                                                                    .withName(
                                                                                                                        "AllowAll")
                                                                                                                    .withAddress(
                                                                                                                        "0.0.0.0")
                                                                                                                    .withSubnetPrefixLength(
                                                                                                                        0)))
                                                                                 )))
                                                            .build())
                           .build()
            );
        } catch (FeignException.Conflict e) {
            logger.info("Live Event already exists. Continuing...");
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

    private void assertLiveEventExists(UUID liveEventId) {
        var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
        try {
            var liveEvent = mediaKindClient.getLiveEvent(sanitisedLiveEventId);
            if (!liveEvent.getProperties().getResourceState().equals(LiveEventResourceState.RUNNING.toString())) {
                throw new LiveEventNotRunningException(sanitisedLiveEventId);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }
    }

    private void assertStreamingEndpointExists(UUID liveEventId) {
        var streamingEndpointName = getShortenedLiveEventId(liveEventId);
        var streamingEndpointBody = MkStreamingEndpoint.builder()
                                                       .location("uksouth")
                                                       .tags(Map.of("environment", this.environmentTag,
                                                                    "application", "pre-recorded evidence"
                                                       ))
                                                       .properties(MkStreamingEndpointProperties.builder()
                                                                                                .description(
                                                                                                    "Streaming Endpoint for " + streamingEndpointName)
                                                                                                .scaleUnits(0)
                                                                                                .sku(
                                                                                                    MkStreamingEndpointSku
                                                                                                        .builder()
                                                                                                        .name(Tier.Standard)
                                                                                                        .build())
                                                                                                .build())
                                                       .build();
        try {
            mediaKindClient.createStreamingEndpoint(streamingEndpointName, streamingEndpointBody);
        } catch (ConflictException e) {
            logger.info("Streaming endpoint already exists");
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {

        try {
            logger.info("Creating Streaming locator");
            var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);

            mediaKindClient.createStreamingLocator(
                sanitisedLiveEventId,
                MkStreamingLocator.builder()
                                  .properties(MkStreamingLocatorProperties.builder()
                                                                          .assetName(getShortenedLiveEventId(liveEventId))
                                                                          .streamingLocatorId(sanitisedLiveEventId)
                                                                          .streamingPolicyName(
                                                                              "Predefined_ClearStreamingOnly")
                                                                          .build()
                                  ).build()
            );
        } catch (ConflictException e) {
            logger.info("Streaming locator already exists");
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(MkStreamingLocatorUrlPaths paths) {
        logger.info("parsing live output url from streaming locator paths");
        paths.getStreamingPaths().forEach(p -> {
            logger.info(String.valueOf(p.getEncryptionScheme()));
            logger.info(String.valueOf(p.getStreamingProtocol()));
            p.getPaths().forEach(logger::info);
        });
        return paths.getStreamingPaths().stream()
                    .filter(p -> p.getEncryptionScheme() == EncryptionScheme.EnvelopeEncryption
                        && p.getStreamingProtocol() == StreamingProtocol.Hls)
                    .flatMap(path -> path.getPaths().stream())
                    .findFirst()
                    .map(p -> "https://" + p)
                    .orElseThrow(() -> new RuntimeException("No valid paths returned from Streaming Locator"));
    }
}
