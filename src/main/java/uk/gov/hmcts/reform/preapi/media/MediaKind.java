package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;

import java.util.List;

@Component
public class MediaKind implements IMediaService {
    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AssetDTO getAsset(String assetName) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<AssetInner> getAssets() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String startLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String playLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String stopLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getLiveEvents() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
