package uk.gov.hmcts.reform.preapi.media;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;

import java.util.List;

@Component
public class MediaKind implements IMediaService {

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
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AssetDTO> getAssets() {
        throw new UnsupportedOperationException();
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
    public String getLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLiveEvents() {
        throw new UnsupportedOperationException();
    }
}
