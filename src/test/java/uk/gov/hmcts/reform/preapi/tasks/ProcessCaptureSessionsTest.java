package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.EncodeJobService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ProcessCaptureSessions.class)
public class ProcessCaptureSessionsTest {
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private EncodeJobService encodeJobService;

    private IMediaService mediaService;
    private ProcessCaptureSessions processCaptureSessions;

    private static final String ROBOT_USER_EMAIL = "example@example.com";

    @BeforeEach
    void setUp() {
        processCaptureSessions = new ProcessCaptureSessions(
            userService,
            userAuthenticationService,
            ROBOT_USER_EMAIL,
            captureSessionService,
            mediaServiceBroker,
            encodeJobService
        );
        mediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString())).thenReturn(Optional.of(userAuth));
    }

    @Test
    @DisplayName("Should ignore jobs when they are still processing")
    public void testRunJobStillProcessing() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.PROCESSING);

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
    }

    @Test
    @DisplayName("Should mark capture session as failure on media service job failure")
    public void testRunJobFailure() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.FAILURE);

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
        verify(encodeJobService, times(1)).delete(dto.getId());
        verify(captureSessionService, times(1))
            .stopCaptureSession(dto.getCaptureSessionId(), RecordingStatus.FAILURE, null);
    }

    @Test
    @DisplayName("Should mark capture session as failure when something goes wrong")
    public void testRunExceptionFailure() {
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        doThrow(new NotFoundException("Something went wrong")).when(mediaService).hasJobCompleted(any(), any());

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName());
        verify(encodeJobService, times(1)).delete(dto.getId());
        verify(captureSessionService, times(1))
            .stopCaptureSession(dto.getCaptureSessionId(), RecordingStatus.FAILURE, null);
    }

    @Test
    @DisplayName("Should start processing step 2 when step 1 is completed")
    public void testRunJob1Complete() {
        var newJobName = "jobName2";
        var dto = createEncodeJobDTO(EncodeTransform.ENCODE_FROM_INGEST);
        when(encodeJobService.findAllProcessing()).thenReturn(List.of(dto));
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, dto.getJobName()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.triggerProcessingStep2(dto.getRecordingId()))
            .thenReturn(newJobName);

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, "jobName");
        verify(mediaService, times(1)).triggerProcessingStep2(dto.getRecordingId());

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
        when(mediaService.triggerProcessingStep2(dto.getRecordingId()))
            .thenReturn(null);

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, "jobName");
        verify(mediaService, times(1)).triggerProcessingStep2(dto.getRecordingId());
        verify(encodeJobService, never()).upsert(any());
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(dto.getCaptureSessionId()), eq(RecordingStatus.NO_RECORDING), isNull());
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

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, "jobName");
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1)).stopCaptureSession(
            eq(dto.getCaptureSessionId()),
            eq(RecordingStatus.RECORDING_AVAILABLE),
            eq(dto.getRecordingId())
        );
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

        processCaptureSessions.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(encodeJobService, times(2)).findAllProcessing();
        verify(mediaService, times(1)).hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, "jobName");
        verify(encodeJobService, times(1)).delete(any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(dto.getCaptureSessionId()), eq(RecordingStatus.FAILURE), isNull());
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
