package uk.gov.hmcts.reform.preapi.media;

import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;

import java.util.List;

public interface IMediaService {
    PlaybackDTO playAsset(String assetId);

    String importAsset(String assetPath);

    AssetDTO getAsset(String assetId);

    List<AssetDTO> getAssets();

    LiveEventDTO getLiveEvent(String liveEventId);

    List<LiveEventDTO> getLiveEvents();

    String startLiveEvent(CaptureSessionDTO captureSession) throws InterruptedException;

    /*
    String playLiveEvent(String liveEventId);

    String stopLiveEvent(String liveEventId);
    */
}
