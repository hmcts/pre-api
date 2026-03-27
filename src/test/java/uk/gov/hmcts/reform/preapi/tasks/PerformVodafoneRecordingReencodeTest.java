package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingReencodeJobService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.services.VodafoneRecordingReencodeService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PerformVodafoneRecordingReencode.class)
class PerformVodafoneRecordingReencodeTest {

    @MockitoBean
    private RecordingReencodeJobService recordingReencodeJobService;

    @MockitoBean
    private VodafoneRecordingReencodeService vodafoneRecordingReencodeService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    private PerformVodafoneRecordingReencode underTest;

    private static final String CRON_USER_EMAIL = "test@test.com";

    @BeforeEach
    void setUp() {
        underTest = new PerformVodafoneRecordingReencode(
            recordingReencodeJobService,
            vodafoneRecordingReencodeService,
            userService,
            userAuthenticationService,
            CRON_USER_EMAIL
        );

        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        var access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(userAuth));
    }

    @Test
    @DisplayName("PerformVodafoneRecordingReencode should process and complete a pending job")
    void runProcessesPendingJob() {
        RecordingReencodeJob job = new RecordingReencodeJob();
        job.setId(UUID.randomUUID());

        when(recordingReencodeJobService.getNextPendingJob()).thenReturn(Optional.of(job));
        when(recordingReencodeJobService.markAsProcessing(job.getId())).thenReturn(job);

        underTest.run();

        verify(vodafoneRecordingReencodeService).processJob(job);
        verify(recordingReencodeJobService).markAsComplete(job.getId());
        verify(recordingReencodeJobService, never()).markAsError(any(), any());
    }

    @Test
    @DisplayName("PerformVodafoneRecordingReencode should mark errors when processing fails")
    void runMarksErrorWhenProcessingFails() {
        RecordingReencodeJob job = new RecordingReencodeJob();
        job.setId(UUID.randomUUID());

        when(recordingReencodeJobService.getNextPendingJob()).thenReturn(Optional.of(job));
        when(recordingReencodeJobService.markAsProcessing(job.getId())).thenReturn(job);
        doThrow(new RuntimeException("boom")).when(vodafoneRecordingReencodeService).processJob(job);

        underTest.run();

        verify(recordingReencodeJobService).markAsError(job.getId(), "boom");
    }

    @Test
    @DisplayName("PerformVodafoneRecordingReencode should skip locked jobs")
    void runSkipsLockedJobs() {
        RecordingReencodeJob job = new RecordingReencodeJob();
        job.setId(UUID.randomUUID());

        when(recordingReencodeJobService.getNextPendingJob()).thenReturn(Optional.of(job));
        doThrow(PessimisticLockingFailureException.class).when(recordingReencodeJobService).markAsProcessing(job.getId());

        underTest.run();

        verify(vodafoneRecordingReencodeService, never()).processJob(any());
        verify(recordingReencodeJobService, never()).markAsComplete(any());
        verify(recordingReencodeJobService, never()).markAsError(any(), any());
    }

    @Test
    @DisplayName("PerformVodafoneRecordingReencode should skip jobs in the wrong state")
    void runSkipsJobsInWrongState() {
        RecordingReencodeJob job = new RecordingReencodeJob();
        job.setId(UUID.randomUUID());

        when(recordingReencodeJobService.getNextPendingJob()).thenReturn(Optional.of(job));
        doThrow(ResourceInWrongStateException.class).when(recordingReencodeJobService).markAsProcessing(job.getId());

        underTest.run();

        verify(vodafoneRecordingReencodeService, never()).processJob(any());
        verify(recordingReencodeJobService, never()).markAsComplete(any());
        verify(recordingReencodeJobService, never()).markAsError(any(), any());
    }
}
