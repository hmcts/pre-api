package uk.gov.hmcts.reform.preapi.media;

public interface IMediaService {
    String playAsset(String assetId);
    String importAsset(String assetPath);
    String getAsset(String assetId);
    String getAssets();
    String startLiveEvent(String liveEventId);
    String playLiveEvent(String liveEventId);
    String stopLiveEvent(String liveEventId);
    String getLiveEvent(String liveEventId);
    String getLiveEvents();
}
