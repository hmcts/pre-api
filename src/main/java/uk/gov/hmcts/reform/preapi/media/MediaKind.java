package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpoint;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpointProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpointSku;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.EncryptionScheme;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol;
import uk.gov.hmcts.reform.preapi.media.dto.Tier;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;
import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getShortenedLiveEventId;


@Service
public class MediaKind implements IMediaService {
    private final MediaKindClient mediaKindClient;
    private final String platformEnv;

    @Autowired
    public MediaKind(MediaKindClient mediaKindClient,
                     @Value("${platform-env}") String platformEnv) {
        this.mediaKindClient = mediaKindClient;
        this.platformEnv = platformEnv;
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
    public String playLiveEvent(UUID liveEventId) {
        assertLiveEventExists(liveEventId);
        assertStreamingEndpointExists(liveEventId);
        try {
            mediaKindClient.startStreamingEndpoint(getShortenedLiveEventId(liveEventId));
        } catch (Exception ex) {
            Logger.getAnonymousLogger().info("Error starting streaming endpoint: " + ex.getMessage());
            throw ex;
        }

        assertStreamingLocatorExists(liveEventId);
        var paths = mediaKindClient.listStreamingLocatorPaths(getSanitisedLiveEventId(liveEventId));

        return parseLiveOutputUrlFromStreamingLocatorPaths(paths);
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
            return new LiveEventDTO(mediaKindClient.getLiveEvent(liveEventName));
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Live event: " + liveEventName);
        }
    }

    @Override
    public List<LiveEventDTO> getLiveEvents() {
        return getAllMkList(mediaKindClient::getLiveEvents)
            .map(LiveEventDTO::new)
            .toList();
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

    private void assertLiveEventExists(@NotNull UUID liveEventId) {
        var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);
        var liveEvent = mediaKindClient.getLiveEvent(sanitisedLiveEventId);
        if (!liveEvent.getProperties().getResourceState().equals(LiveEventResourceState.RUNNING.toString())) {
            throw new LiveEventNotRunningException(sanitisedLiveEventId);
        }
    }

    private void assertStreamingEndpointExists(@NotNull UUID liveEventId) {
        var streamingEndpointName = getShortenedLiveEventId(liveEventId);
        var streamingEndpointBody = new MkStreamingEndpoint()
            .location("UKSouth")
            .tags(Map.of("environment", this.platformEnv,
                   "application", "pre-recorded evidence",
                   "businessArea", "cross-cutting",
                   "builtFrom", "azure portal"))
            .properties(new MkStreamingEndpointProperties()
                            .description("Streaming Endpoint for " + streamingEndpointName)
                            .scaleUnits(0)
                            .sku(new MkStreamingEndpointSku().name(Tier.Standard)));
        mediaKindClient.createStreamingEndpoint(streamingEndpointName, streamingEndpointBody);
    }

    private void assertStreamingLocatorExists(UUID liveEventId) {

        try {
            Logger.getAnonymousLogger().info("Creating Streaming locator");
            var sanitisedLiveEventId = getSanitisedLiveEventId(liveEventId);

            mediaKindClient.createStreamingLocator(sanitisedLiveEventId,
                                                   new MkStreamingLocator()
                                                       .properties(new MkStreamingLocatorProperties()
                                                           .assetName(sanitisedLiveEventId)
                                                           .streamingLocatorId(sanitisedLiveEventId)
                                                           .streamingPolicyName("Predefined_ClearStreamingOnly")
                                                       ));
        } catch (FeignException.Conflict e) {
            Logger.getAnonymousLogger().info("Streaming locator already exists");
        }
    }

    private String parseLiveOutputUrlFromStreamingLocatorPaths(@NotNull MkStreamingLocatorUrlPaths paths) {
        Logger.getAnonymousLogger().info("parsing live output url from streaming locator paths");
        paths.getStreamingPaths().forEach(p -> {
            Logger.getAnonymousLogger().info(String.valueOf(p.getEncryptionScheme()));
            Logger.getAnonymousLogger().info(String.valueOf(p.getStreamingProtocol()));
            p.getPaths().forEach(Logger.getAnonymousLogger()::info);
        });
        return paths.getStreamingPaths().stream()
            .filter(p -> p.getEncryptionScheme() == EncryptionScheme.EnvelopeEncryption
            && p.getStreamingProtocol() == StreamingProtocol.Hls)
                    .flatMap(path -> path.getPaths().stream())
                    .findFirst()
                    .map(p -> "https://" + p)
                    .orElseThrow(() -> new RuntimeException("Unable to create streaming locator"));
    }
}
