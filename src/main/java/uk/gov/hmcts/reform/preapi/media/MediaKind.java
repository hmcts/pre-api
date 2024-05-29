package uk.gov.hmcts.reform.preapi.media;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;

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
    public LiveEventDTO getLiveEvent(String liveEventName) {
        try {
            return new LiveEventDTO(mediaKindClient.getLiveEvent(liveEventName));
        } catch (FeignException.NotFound e) {
            return null;
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
}
