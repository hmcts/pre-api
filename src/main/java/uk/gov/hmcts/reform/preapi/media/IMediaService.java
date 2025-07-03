package uk.gov.hmcts.reform.preapi.media;

import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.util.List;
import java.util.UUID;

public interface IMediaService {
    PlaybackDTO playAsset(String assetId, String userId) throws InterruptedException;

    String playLiveEvent(UUID liveEventId) throws InterruptedException;

    GenerateAssetResponseDTO importAsset(GenerateAssetDTO generateAssetDTO, boolean sourceIsFinalStorage)
        throws InterruptedException;

    AssetDTO getAsset(String assetId);

    List<AssetDTO> getAssets();

    LiveEventDTO getLiveEvent(String liveEventId);

    List<LiveEventDTO> getLiveEvents();

    void startLiveEvent(CaptureSessionDTO captureSession);

    void stopLiveEvent(CaptureSessionDTO captureSession, UUID recordingId) throws InterruptedException;

    void stopLiveEvent(String liveEventId);

    RecordingStatus stopLiveEventAndProcess(CaptureSessionDTO captureSession, UUID recordingId)
        throws InterruptedException;

    void cleanupStoppedLiveEvent(String liveEventId);

    void deleteAllStreamingLocatorsAndContentKeyPolicies();

    String triggerProcessingStep1(CaptureSessionDTO captureSession, String captureSessionNoHyphen, UUID recordingId);

    String triggerProcessingStep2(UUID recordingId);

    RecordingStatus verifyFinalAssetExists(UUID recordingId);

    RecordingStatus hasJobCompleted(String transformName, String jobName);
}
