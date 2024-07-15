package uk.gov.hmcts.reform.preapi.media;

import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.util.List;
import java.util.UUID;

public interface IMediaService {
    String playAsset(String assetId);

    String playLiveEvent(UUID liveEventId);

    String importAsset(String assetPath);

    AssetDTO getAsset(String assetId);

    List<AssetDTO> getAssets();

    LiveEventDTO getLiveEvent(String liveEventId);

    List<LiveEventDTO> getLiveEvents();

    void startLiveEvent(CaptureSessionDTO captureSession);

    RecordingStatus stopLiveEvent(CaptureSessionDTO captureSession, UUID recordingId) throws InterruptedException;
}
