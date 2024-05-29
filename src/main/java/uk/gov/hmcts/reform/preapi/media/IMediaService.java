package uk.gov.hmcts.reform.preapi.media;

import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;

import java.util.List;

public interface IMediaService {
    String playAsset(String assetId);

    String importAsset(String assetPath);

    AssetDTO getAsset(String assetId);

    List<AssetDTO> getAssets();

    void startLiveEvent(String liveEventId);

    String playLiveEvent(String liveEventId);

    void stopLiveEvent(String liveEventId);

    LiveEventDTO getLiveEvent(String liveEventId);

    List<LiveEventDTO> getLiveEvents();

}
