package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;

@SpringBootTest(classes = RegistrationService.class)
public class RegistrationServiceTest {

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private IMediaService mediaService;

    private final UUID recordingId = UUID.randomUUID();
    private final UUID captureSessionId = UUID.randomUUID();
    private final String ingestJobName = format("%s_temp", getSanitisedLiveEventId(captureSessionId));

    private RegistrationService underTest;

    @BeforeEach
    void setUp() {
        mediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        underTest = new RegistrationService(captureSessionService, mediaServiceBroker, azureIngestStorageService);
    }

    @Test
    @DisplayName("Registering: Should throw exception when a running live event is found for capture session")
    public void testExceptionWhenActiveLiveEventFound() {
        LiveEventDTO liveEventDTO = mock(LiveEventDTO.class);
        when(mediaService.getLiveEvent(ingestJobName)).thenReturn(liveEventDTO);

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(captureSessionId)
        );
    }

    @Test
    @DisplayName("Registering: Should throw exception when registering capture session and ingest job is incomplete")
    public void testRegistrationExceptionWhenIngestJobIncomplete() {
        when(mediaService.getLiveEvent(ingestJobName)).thenThrow(new NotFoundException("No live event found"));

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName))
            .thenReturn(RecordingStatus.PROCESSING);

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(captureSessionId)
        );
    }

    @Test
    @DisplayName("Registering: Should throw exception when no ingest job assets found")
    public void testExceptionWhenNoIngestJobAssetsFound() {

        when(mediaService.getLiveEvent(ingestJobName))
            .thenThrow(new NotFoundException("No live event found"));

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM,
                                          format("%s_temp", ingestJobName)
        ))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        when(mediaService.getJobOutputAssets(MediaKind.ENCODE_FROM_INGEST_TRANSFORM,
                                          format("%s_temp", ingestJobName)))
            .thenReturn(List.of());

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(captureSessionId)
        );
    }

    @Test
    @DisplayName("Registering: should throw exception when registering capture session if recording not found")
    public void testRegistrationExceptionWhenNoRecordingFound() {
        when(mediaService.getLiveEvent(ingestJobName))
            .thenThrow(new NotFoundException("No live event found"));

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        when(mediaService.getJobOutputAssets(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName))
            // TODO: return a JobOutputAsset with a recording ID
            .thenReturn(List.of());

        // TODO: When mediaService.verifyFinalAssetExists(recordingID) then return RecordingStatus.FAILURE;

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(captureSessionId)
        );

        verify(mediaService, times(1))
            .hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName);
        verify(mediaService, times(1)).verifyFinalAssetExists(any());
    }

    @Test
    @DisplayName("Registering: should successfully register capture session if jobs completed and recording found")
    public void testRegistrationWhenEncodingIsCompleted() {
        when(mediaService.getLiveEvent(ingestJobName))
            .thenThrow(new NotFoundException("No live event found"));

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        when(mediaService.getJobOutputAssets(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName))
            // TODO: return a JobOutputAsset with recordingId
            .thenReturn(List.of());

        when(mediaService.verifyFinalAssetExists(recordingId)).thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        CaptureSessionDTO captureSessionDto = mock(CaptureSessionDTO.class);
        UUID bookingId = UUID.randomUUID();
        when(captureSessionDto.getBookingId()).thenReturn(bookingId);
        when(captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.RECORDING_AVAILABLE,
                                                      recordingId)).thenReturn(captureSessionDto);

        UpsertResult register = underTest.register(captureSessionId);

        assertThat(register).isEqualTo(UpsertResult.UPDATED);

        verify(mediaService, times(1))
            .hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, ingestJobName);
        verify(mediaService, times(1)).verifyFinalAssetExists(recordingId);
        verify(captureSessionService, times(1))
            .stopCaptureSession(captureSessionId, RecordingStatus.RECORDING_AVAILABLE, recordingId);

        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(bookingId.toString());
        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(recordingId.toString());
    }

}
