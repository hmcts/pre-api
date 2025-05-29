package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CheckForMissingRecordingsTest {

    private CheckForMissingRecordings checkForMissingRecordingsTask;
    private CaptureSessionService captureSessionService;
    private SlackClient slackClient;
    private RecordingService recordingService;
    private static final String ROBOT_USER_EMAIL = "example@example.com";
    private AzureFinalStorageService azureFinalStorageService;

    private Booking booking;
    private User user;

    @BeforeEach
    public void setUp() {
        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));

        slackClient = mock(SlackClient.class);
        recordingService = mock(RecordingService.class);
        captureSessionService = mock(CaptureSessionService.class);
        azureFinalStorageService = mock(AzureFinalStorageService.class);

        UserService userService = mock(UserService.class);
        UserAuthenticationService userAuthenticationService = mock(UserAuthenticationService.class);
        UserAuthentication userAuth = mock(UserAuthentication.class);

        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuth));

        String platformEnv = "Local-Testing";

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        Case testCase = HelperFactory.createCase(
            court, "ref1234", true,
            new Timestamp(System.currentTimeMillis())
        );
        booking = HelperFactory.createBooking(
            testCase, Timestamp.valueOf(LocalDateTime.now()),
            new Timestamp(System.currentTimeMillis())
        );
        user = HelperFactory.createDefaultTestUser();

        checkForMissingRecordingsTask = new CheckForMissingRecordings(
            captureSessionService,
            slackClient,
            userService,
            userAuthenticationService,
            ROBOT_USER_EMAIL,
            recordingService,
            platformEnv,
            azureFinalStorageService
        );
    }

    @Test
    public void testRecordingsWithAvailableStatus() {
        final CaptureSession captureSessionZeroRec =
            createCaptureSessionForStatus(RecordingStatus.RECORDING_AVAILABLE);
        RecordingDTO zeroDurationRecording = createRecording(0, captureSessionZeroRec);
        zeroDurationRecording.setDuration(Duration.ZERO);

        final CaptureSession captureSessionWithoutRecording =
            createCaptureSessionForStatus(RecordingStatus.RECORDING_AVAILABLE);

        final CaptureSession captureSessionRecNotInSA =
            createCaptureSessionForStatus(RecordingStatus.RECORDING_AVAILABLE);
        RecordingDTO recordingNotInSA = createRecording(3, captureSessionRecNotInSA);
        recordingNotInSA.setDuration(Duration.ofMinutes(3));

        final CaptureSession captureSessionNoLiveOutputUrl =
            createCaptureSessionForStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionNoLiveOutputUrl.setLiveOutputUrl(null);
        RecordingDTO noLiveUrl = createRecording(3, captureSessionNoLiveOutputUrl);
        noLiveUrl.setDuration(Duration.ofMinutes(3));

        final CaptureSession captureSessionAvailable =
            createCaptureSessionForStatus(RecordingStatus.RECORDING_AVAILABLE);
        RecordingDTO normalDurationRecording = createRecording(3, captureSessionAvailable);
        normalDurationRecording.setDuration(Duration.ofMinutes(3));

        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(
                zeroDurationRecording,
                recordingNotInSA,
                normalDurationRecording,
                noLiveUrl
            )));

        when(azureFinalStorageService.getRecordingDuration(normalDurationRecording.getId()))
            .thenReturn(Duration.of(3, ChronoUnit.MINUTES));

        when(azureFinalStorageService.getRecordingDuration(noLiveUrl.getId()))
            .thenReturn(Duration.of(3, ChronoUnit.MINUTES));

        when(azureFinalStorageService.getRecordingDuration(recordingNotInSA.getId()))
            .thenReturn(null);

        when(captureSessionService.findAvailableSessionsByDate(any(LocalDate.class))).thenReturn(Arrays.asList(
            captureSessionZeroRec,
            captureSessionWithoutRecording,
            captureSessionRecNotInSA,
            captureSessionNoLiveOutputUrl,
            captureSessionAvailable
        ));
        checkForMissingRecordingsTask.run();

        verify(azureFinalStorageService, times(1))
            .getRecordingDuration(recordingNotInSA.getId());
        verify(azureFinalStorageService, times(1))
            .getRecordingDuration(normalDurationRecording.getId());
        verify(captureSessionService, times(1))
            .findAvailableSessionsByDate(any(LocalDate.class));
        verify(slackClient, times(1)).postSlackMessage(anyString());

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());

        assertThat(slackCaptor.getValue())
            .contains("\\n\\n:warning: *Capture sessions: RECORDING_AVAILABLE but with problems:*\\n");

        assertThat(slackCaptor.getValue())
            .contains("\\nRecording for capture session "
                          + captureSessionZeroRec.getId() + " has zero duration in database\\n");

        assertThat(slackCaptor.getValue())
            .contains("\\nMissing recording for capture session " + captureSessionWithoutRecording.getId()
                          + ": not in database\\n");

        assertThat(slackCaptor.getValue())
            .contains("\\nCapture session " + captureSessionNoLiveOutputUrl.getId()
                          + " missing live output url\\n");

        assertThat(slackCaptor.getValue())
            .contains("Missing recording for capture session " + captureSessionRecNotInSA.getId()
                          + ": not in final SA\\n");
    }

    @Test
    public void testNoMissingRecordingsAreFound() {
        when(captureSessionService.findAvailableSessionsByDate(any(LocalDate.class)))
            .thenReturn(Collections.emptyList());
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1))
            .findAvailableSessionsByDate(any(LocalDate.class));
        verify(recordingService, times(0))
            .findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged()));
        verify(slackClient, times(0)).postSlackMessage(anyString());
    }

    @Test
    public void testWithNonAvailableRecordings() {
        final CaptureSession processingStatus = createCaptureSessionForStatus(RecordingStatus.PROCESSING);
        final CaptureSession failedStatus = createCaptureSessionForStatus(RecordingStatus.FAILURE);
        final CaptureSession noRecording = createCaptureSessionForStatus(RecordingStatus.NO_RECORDING);

        when(captureSessionService.findAvailableSessionsByDate(any(LocalDate.class)))
            .thenReturn(List.of(processingStatus, failedStatus, noRecording));
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1))
            .findAvailableSessionsByDate(any(LocalDate.class));
        verify(recordingService, times(0))
            .findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged()));
        verify(slackClient, times(1)).postSlackMessage(anyString());

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());

        assertThat(slackCaptor.getValue())
            .contains("\\n\\n:warning: *Capture sessions with NO_RECORDING status:*\\n" + noRecording.getId());

        assertThat(slackCaptor.getValue())
            .contains("\\n\\n:warning: *Capture sessions with FAILURE status:*\\n" + failedStatus.getId());

        assertThat(slackCaptor.getValue())
            .contains("\\n\\n:warning: *Capture sessions with PROCESSING status:*\\n" + processingStatus.getId());
    }

    private CaptureSession createCaptureSessionForStatus(RecordingStatus recordingStatus) {
        return HelperFactory.createCaptureSession(
            booking, RecordingOrigin.PRE, "TestIngestAddress", "TestLiveOutputAddress",
            new Timestamp(System.currentTimeMillis()), user,
            new Timestamp(System.currentTimeMillis()), user,
            recordingStatus,
            new Timestamp(System.currentTimeMillis())
        );
    }

    private RecordingDTO createRecording(Integer duration, CaptureSession captureSession) {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setDuration(Duration.of(duration, ChronoUnit.MINUTES));
        recording.setCaptureSession(new CaptureSessionDTO(captureSession));
        return recording;
    }

}
