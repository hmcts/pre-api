package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;

import java.util.List;
import java.util.UUID;

public interface IMediaService {
    PlaybackDTO playAsset(String assetId, String userId) throws InterruptedException;

    String playLiveEvent(UUID liveEventId) throws InterruptedException;

    GenerateAssetResponseDTO importAsset(GenerateAssetDTO generateAssetDTO, boolean sourceIsFinalStorage)
        throws InterruptedException;

    boolean importAsset(RecordingDTO recordingDTO, boolean isFinal);

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

    String triggerProcessingStep2(UUID recordingId, boolean isImport);

    RecordingStatus verifyFinalAssetExists(UUID recordingId);

    RecordingStatus hasJobCompleted(String transformName, String jobName);

    List<JobOutputAsset> getJobOutputAssets(String transformName, String jobName);

    MkJob getJobFromPartialName(String transformName, String jobNamePartial);

    boolean checkLiveFeedAvailable(UUID captureSessionId);
}
