package uk.gov.hmcts.reform.preapi.media;

import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;

import java.util.List;
import java.util.UUID;

public interface IMediaService {
    String playAsset(String assetId);

    String importAsset(String assetPath);

    AssetDTO getAsset(String assetId);

    List<AssetDTO> getAssets();

    CaptureSessionDTO startLiveEvent(UUID captureSessionId);
    /*
    String playLiveEvent(String liveEventId);

    String stopLiveEvent(String liveEventId);

    String getLiveEvent(String liveEventId);

    String getLiveEvents();
     */
}
