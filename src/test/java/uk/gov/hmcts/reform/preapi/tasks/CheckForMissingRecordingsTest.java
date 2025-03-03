package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static java.lang.String.format;
import static org.mockito.Mockito.*;

public class CheckForMissingRecordingsTest {

    private CheckForMissingRecordings checkForMissingRecordingsTask;
    private CaptureSessionService captureSessionService;
    private SlackClient slackClient;
    private RecordingService recordingService;
    private static final String ROBOT_USER_EMAIL = "example@example.com";

    @BeforeEach
    public void setUp() {
        String platformEnv = "Local-Testing";

        slackClient = mock(SlackClient.class);
        recordingService = mock(RecordingService.class);
        UserService userService = mock(UserService.class);
        UserAuthenticationService userAuthenticationService = mock(UserAuthenticationService.class);
        captureSessionService = mock(CaptureSessionService.class);

        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuth));

        checkForMissingRecordingsTask = new CheckForMissingRecordings(
            captureSessionService,
            slackClient,
            userService,
            userAuthenticationService,
            ROBOT_USER_EMAIL,
            recordingService,
            platformEnv
        );
    }

    @Test
    public void testMissingRecordingsAreFound() {
        UUID zeroId = UUID.randomUUID();
        UUID happyId = UUID.randomUUID();
        UUID noCSId = UUID.randomUUID();
        UUID noRecId = UUID.randomUUID();

        RecordingDTO zeroDurationRecording = new RecordingDTO();
        zeroDurationRecording.setId(zeroId);
        zeroDurationRecording.setDuration(Duration.ZERO);

        RecordingDTO happyRecording = new RecordingDTO();
        happyRecording.setId(happyId);
        happyRecording.setDuration(Duration.ofMinutes(3));

        RecordingDTO recordingWithoutACaptureSession = new RecordingDTO();
        recordingWithoutACaptureSession.setId(noCSId);
        recordingWithoutACaptureSession.setDuration(Duration.ofMinutes(1));

        CaptureSession captureSession1 = new CaptureSession();
        Booking booking1 = new Booking();
        booking1.setId(zeroId);
        captureSession1.setId(UUID.randomUUID());
        captureSession1.setBooking(booking1);

        CaptureSession captureSession2 = new CaptureSession();
        Booking booking2 = new Booking();
        booking2.setId(happyId);
        captureSession2.setId(UUID.randomUUID());
        captureSession2.setBooking(booking2);

        CaptureSession captureSession3 = new CaptureSession();
        Booking booking3 = new Booking();
        booking3.setId(noRecId);
        captureSession3.setId(UUID.randomUUID());
        captureSession3.setBooking(booking3);

        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(
                recordingWithoutACaptureSession,
                zeroDurationRecording,
                happyRecording
            )));

        when(captureSessionService.findAvailableSessionsByDate(any(LocalDate.class))).thenReturn(Arrays.asList(
            captureSession1,
            captureSession2,
            captureSession3
        ));
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findAvailableSessionsByDate(any(LocalDate.class));
        verify(slackClient, times(1)).postSlackMessage(format(
            "{\"text\":\":globe_with_meridians: *Environment:* Local-Testing\\n\\n" +
                ":warning: *Missing Recordings:*" +
                "\\n\\n\\t:siren: %s :siren:\\n\\n" +
                ":warning: *Zero-Duration Recordings:*" +
                "\\n\\n\\t:siren: %s :siren:\\n\\n\"}", noRecId, zeroId
        ));
    }

    @Test
    public void testNoMissingRecordingsAreFound() {
        when(captureSessionService.findAvailableSessionsByDate(any(LocalDate.class))).thenReturn(Collections.emptyList());
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findAvailableSessionsByDate(any(LocalDate.class));
        verify(recordingService, times(0)).findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged()));
        verify(slackClient, times(0)).postSlackMessage(anyString());
    }

}
