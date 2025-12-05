package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CaptureSessionStatusCorrectionTaskTest {

    private CaptureSessionStatusCorrectionTask captureSessionStatusCorrectionTask;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private AzureIngestStorageService azureIngestStorageService;
    private CaptureSessionService captureSessionService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        azureIngestStorageService = mock(AzureIngestStorageService.class);
        captureSessionService = mock(CaptureSessionService.class);
        captureSessionStatusCorrectionTask = new CaptureSessionStatusCorrectionTask(
            userService,
            userAuthenticationService,
            "robot",
            azureIngestStorageService,
            captureSessionService
        );
    }

    @Test
    void shouldCorrectCaptureSessionStatuses() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );

        captureSessionStatusCorrectionTask.correctCaptureSessionStatuses(
            List.of(captureSession1, captureSession2)
        );

        assertThat(captureSession1.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
        assertThat(captureSession2.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);

    }

    @Test
    void shouldCorrectOneCaptureSessionStatus() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        captureSessionStatusCorrectionTask.correctCaptureSessionStatuses(
            List.of(captureSession1)
        );

        assertThat(captureSession1.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
    }

    @Test
    void shouldFilterUnusedCaptureSessionsBySectionFile() {
        Booking booking = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking.setId(UUID.fromString("786c62de-a8c9-448d-919c-0038061413d5"));

        Booking booking2 = HelperFactory.createBooking(
            null,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis())
        );

        booking2.setId(UUID.fromString("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0"));

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        when(azureIngestStorageService.sectionFileExist("786c62de-a8c9-448d-919c-0038061413d5")).thenReturn(true);
        when(azureIngestStorageService.sectionFileExist("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0")).thenReturn(false);

        List<CaptureSession> results = captureSessionStatusCorrectionTask.filterUnusedCaptureSessionsBySectionFile(
            List.of(captureSession1, captureSession2));

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.getFirst().getBooking().getId())
            .isEqualTo(UUID.fromString("04ab4e94-bb8a-42cd-929f-1e3e02b54bc0"));
    }

    @Test
    void shouldHandleFilteringNullCaptureSessions() {
        CaptureSession captureSession1 = null;
        CaptureSession captureSession2 = null;
        List<CaptureSession> results = captureSessionStatusCorrectionTask.filterUnusedCaptureSessionsBySectionFile(
            Arrays.asList(captureSession1, captureSession2));

        assertThat(results.size()).isEqualTo(0);
    }

    @Test
    void shouldHandleFilteringCaptureSessionsWithNullBooking() {
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            null,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            null,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        List<CaptureSession> results = captureSessionStatusCorrectionTask.filterUnusedCaptureSessionsBySectionFile(
            List.of(captureSession1, captureSession2));

        assertThat(results.size()).isEqualTo(0);
    }

    @Test
    void getFailedCaptureSessions() {
        captureSessionStatusCorrectionTask.getFailedCaptureSessions();

        verify(captureSessionService, times(1))
            .findFailedCaptureSessionsStartedBetween(any(LocalDate.class), any(LocalDate.class));
    }
}
