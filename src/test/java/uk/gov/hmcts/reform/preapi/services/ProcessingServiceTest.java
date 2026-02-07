package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ProcessingService.class)
public class ProcessingServiceTest {

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private EncodeJobService encodeJobService;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private IMediaService mediaService;

    private ProcessingService underTest;

    @BeforeEach
    void setUp() {
        mediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        underTest = new ProcessingService(
            captureSessionService, mediaServiceBroker,
            encodeJobService, azureIngestStorageService
        );
    }

    @Test
    @DisplayName("Should ignore jobs when they are still processing")
    public void testRunJobStillProcessing() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.PROCESSING);

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
        verifyNoInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should mark capture session as failure on media service job failure")
    public void testRunJobFailure() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.FAILURE);

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
        verify(encodeJobService, times(1)).delete(dto.getId());
        verify(captureSessionService, times(1))
            .stopCaptureSession(dto.getCaptureSessionId(), RecordingStatus.FAILURE, null);
        verifyNoInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should mark capture session as failure when something goes wrong")
    public void testRunExceptionFailure() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        doThrow(new NotFoundException("Something went wrong")).when(mediaService).hasJobCompleted(any(), any());

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
        verify(encodeJobService, times(1)).delete(dto.getId());
        verify(captureSessionService, times(1))
            .stopCaptureSession(dto.getCaptureSessionId(), RecordingStatus.FAILURE, null);
        verifyNoInteractions(azureIngestStorageService);
    }


    @Test
    @DisplayName("Should start processing step 2 when step 1 is completed")
    public void testRunJob1Complete() {
        var newJobName = "jobName2";
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.triggerProcessingStep2(dto.getRecordingId(), false))
            .thenReturn(newJobName);

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, "jobName");
        verify(mediaService, times(1)).triggerProcessingStep2(dto.getRecordingId(), false);
        verifyNoInteractions(azureIngestStorageService);

        var argumentCaptor = ArgumentCaptor.forClass(EncodeJobDTO.class);
        verify(encodeJobService, times(1)).upsert(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().getJobName()).isEqualTo(newJobName);
        assertThat(argumentCaptor.getValue().getTransform()).isEqualTo(EncodeTransform.ENCODE_FROM_MP4);
    }

    @Test
    @DisplayName("Should mark capture session has no recording when no recording is found")
    public void testRunJob1CompleteNoRecording() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.triggerProcessingStep2(dto.getRecordingId(), false))
            .thenReturn(null);

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, "jobName");
        verify(mediaService, times(1)).triggerProcessingStep2(dto.getRecordingId(), false);
        verify(encodeJobService, never()).upsert(any());
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(dto.getCaptureSessionId()), eq(RecordingStatus.NO_RECORDING), isNull());
        verifyNoInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should mark capture session has recording once recording has been found on step 2")
    public void testRunJob2CompleteRecordingAvailable() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(dto.getRecordingId()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        CaptureSessionDTO captureSessionDTO = new CaptureSessionDTO();
        captureSessionDTO.setBookingId(UUID.randomUUID());
        when(captureSessionService.stopCaptureSession(any(), any(), any())).thenReturn(captureSessionDTO);

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, "jobName");
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1)).stopCaptureSession(
            eq(dto.getCaptureSessionId()),
            eq(RecordingStatus.RECORDING_AVAILABLE),
            eq(dto.getRecordingId())
        );
        verify(azureIngestStorageService, times(2)).markContainerAsSafeToDelete(any());
    }

    @Test
    @DisplayName("Should mark capture session has failure when file cannot be found on step 2")
    public void testRunJob2CompleteNotFound() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(dto.getRecordingId()))
            .thenReturn(RecordingStatus.FAILURE);

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, "jobName");
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(dto.getCaptureSessionId()), eq(RecordingStatus.FAILURE), isNull());
        verifyNoInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should mark capture session as failure when processing job has timed out")
    public void testJobTimedOut() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);
        dto.setCreatedAt(Timestamp.from(Instant.now().minus(3, ChronoUnit.HOURS)));

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.PROCESSING);

        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));

        underTest.processAllCaptureSessions();

        verify(encodeJobService, times(2)).findAllProcessing();
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(dto.getCaptureSessionId()), eq(RecordingStatus.FAILURE), isNull());
        verifyNoInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should throw exception when registering capture session with no processing jobs")
    public void testExceptionWhenNoJobsFoundForRegistration() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);
        dto.setId(UUID.fromString("ab45b666-dddc-481b-b5eb-081fd65b40a5"));
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));

        assertThrows(
            NotFoundException.class,
            () -> underTest.register(UUID.fromString("f1f57736-6e7e-4401-bf1c-bf6cec6daf52"))
        );

        verify(encodeJobService, times(1)).findAllProcessing();
    }


    @Test
    @DisplayName("Should throw exception when registering capture session with too many processing jobs")
    public void testExceptionWhenTooManyJobsFoundForRegistration() {
        var dto1 = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);
        var dto2 = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);
        var dto3 = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);

        dto2.setCaptureSessionId(dto1.getCaptureSessionId());
        dto3.setCaptureSessionId(dto2.getCaptureSessionId());

        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto1, dto2, dto3));

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(dto1.getCaptureSessionId())
        );

        verify(encodeJobService, times(1)).findAllProcessing();
    }

    @Test
    @DisplayName("Should throw exception when registering capture session if processing job is not completed")
    public void testExceptionWhenJobIncompleteForRegistration() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.PROCESSING);

        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(dto.getCaptureSessionId())
        );

        verify(encodeJobService, times(1)).findAllProcessing();
    }

    @Test
    @DisplayName("Should throw exception when registering capture session if final processing job not found")
    public void testRegistrationExceptionWhenNoFinalProcessingJob() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.PROCESSING);

        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(dto.getCaptureSessionId())
        );


        verify(encodeJobService, times(1)).findAllProcessing();
        verify(mediaService, times(1))
            .hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
        verify(mediaService, times(0)).verifyFinalAssetExists(dto.getRecordingId());
    }

    @Test
    @DisplayName("Should throw exception when registering capture session if recording not found")
    public void testRegistrationExceptionWhenNoRecordingFound() {
        var dto1 = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto1.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(dto1.getRecordingId())).thenReturn(RecordingStatus.NO_RECORDING);

        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto1));

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(dto1.getCaptureSessionId())
        );

        verify(encodeJobService, times(1)).findAllProcessing();
        verify(mediaService, times(1))
            .hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto1.getJobName());
        verify(mediaService, times(1)).verifyFinalAssetExists(dto1.getRecordingId());
    }

    @Test
    @DisplayName("Should successfully register capture session if jobs completed and recording found")
    public void testRegistrationWhenEncodingIsCompleted() {
        var dto1 = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_MP4);

        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto1.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(dto1.getRecordingId()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        CaptureSessionDTO captureSessionDTO = new CaptureSessionDTO();
        captureSessionDTO.setBookingId(UUID.randomUUID());
        when(captureSessionService.stopCaptureSession(any(), any(), any())).thenReturn(captureSessionDTO);


        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto1));


        UpsertResult register = underTest.register(dto1.getCaptureSessionId());

        assertThat(register).isEqualTo(UpsertResult.UPDATED);

        verify(encodeJobService, times(1)).findAllProcessing();
        verify(mediaService, times(1))
            .hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, dto1.getJobName());
        verify(mediaService, times(1)).verifyFinalAssetExists(dto1.getRecordingId());
        verify(captureSessionService, times(1))
            .stopCaptureSession(dto1.getCaptureSessionId(), RecordingStatus.RECORDING_AVAILABLE, dto1.getRecordingId());

        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(captureSessionDTO.getBookingId().toString());
        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(dto1.getRecordingId().toString());
    }

    private EncodeJobDTO createEncodeJobDTO(EncodeTransform transform) {
        var dto = new EncodeJobDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaptureSessionId(UUID.randomUUID());
        dto.setRecordingId(UUID.randomUUID());
        dto.setJobName("jobName");
        dto.setTransform(transform);
        dto.setCreatedAt(Timestamp.from(Instant.now()));
        return dto;
    }


}
