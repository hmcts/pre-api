package uk.gov.hmcts.reform.preapi.media;

import org.springframework.stereotype.Component;

@Component
public class AzureMediaService implements IMediaService {
    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAsset(String assetId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAssets() {
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
