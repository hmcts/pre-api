package uk.gov.hmcts.reform.preapi.media;

import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;

import java.util.List;

public interface IMediaService {
    PlaybackDTO playAsset(String assetId);

    String importAsset(String assetPath);

    AssetDTO getAsset(String assetId);

    List<AssetDTO> getAssets();

    LiveEventDTO getLiveEvent(String liveEventId);

    List<LiveEventDTO> getLiveEvents();

    /*
    String startLiveEvent(String liveEventId);

    String playLiveEvent(String liveEventId);

    String stopLiveEvent(String liveEventId);
    */

}
