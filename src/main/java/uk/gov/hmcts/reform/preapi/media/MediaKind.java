package uk.gov.hmcts.reform.preapi.media;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkCreateStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorPaths;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MediaKind implements IMediaService {
    private final MediaKindClient mediaKindClient;

    @Autowired
    public MediaKind(MediaKindClient mediaKindClient) {
        this.mediaKindClient = mediaKindClient;
    }

    @Override
    public PlaybackDTO playAsset(String assetName) {
        if (getAsset(assetName) == null) {
            throw new NotFoundException("Asset: " + assetName);
        }

        var locators = mediaKindClient.getAssetStreamingLocators(assetName);
        var userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId().toString();

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
                mediaKindClient.putStreamingLocator(userId, MkCreateStreamingLocator.builder().properties(properties).build());
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

        var dash = paths.getStreamingPaths().stream().filter(p -> p.getStreamingProtocol().equals("Dash")).findFirst().orElse(null);
        var hls = paths.getStreamingPaths().stream().filter(p -> p.getStreamingProtocol().equals("Hls")).findFirst().orElse(null);

        return new PlaybackDTO(
            dash != null ? hostName + dash.getPaths().getFirst() : null,
            hls != null ? hostName + hls.getPaths().getFirst() : null
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

    /*
    @Override
    public String startLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

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
