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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
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

    @BeforeEach
    public void setUp() {
        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));

        slackClient = mock(SlackClient.class);
        recordingService = mock(RecordingService.class);
        captureSessionService = mock(CaptureSessionService.class);

        UserService userService = mock(UserService.class);
        UserAuthenticationService userAuthenticationService = mock(UserAuthenticationService.class);
        UserAuthentication userAuth = mock(UserAuthentication.class);

        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuth));

        String platformEnv = "Local-Testing";

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

    private RecordingDTO createRecording(Integer duration, UUID bookingId) {
        RecordingDTO zeroDurationRecording = new RecordingDTO();
        zeroDurationRecording.setId(bookingId);
        zeroDurationRecording.setDuration(Duration.of(duration, ChronoUnit.MINUTES));
        return zeroDurationRecording;
    }

    private CaptureSession createCaptureSession(UUID bookingId) {
        CaptureSession captureSession = new CaptureSession();
        Booking booking = new Booking();
        booking.setId(bookingId);
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        return captureSession;
    }

    @Test
    public void testMissingRecordingsAreFound() {
        UUID zeroId = UUID.randomUUID();
        RecordingDTO zeroDurationRecording = createRecording(0, zeroId);
        CaptureSession zeroDurationCS = createCaptureSession(zeroId);

        UUID noRecId = UUID.randomUUID();
        CaptureSession csWithoutRecording = createCaptureSession(noRecId);

        UUID happyId = UUID.randomUUID();
        RecordingDTO happyRecording = createRecording(3, happyId);
        CaptureSession happyCS = createCaptureSession(happyId);

        UUID noCSId = UUID.randomUUID();
        RecordingDTO recordingWithoutACaptureSession = createRecording(3, noCSId);

        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(
                recordingWithoutACaptureSession,
                zeroDurationRecording,
                happyRecording
            )));

        when(captureSessionService.findAvailableSessionsByDate(any(LocalDate.class))).thenReturn(Arrays.asList(
            zeroDurationCS,
            csWithoutRecording,
            happyCS
        ));
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findAvailableSessionsByDate(any(LocalDate.class));
        verify(slackClient, times(1)).postSlackMessage(format(
            "{\"text\":\":globe_with_meridians: *Environment:* Local-Testing\\n\\n"
                + ":warning: *Missing Recordings:*"
                + "\\n\\n\\t:siren: %s :siren:\\n\\n"
                + ":warning: *Zero-Duration Recordings:*"
                + "\\n\\n\\t:siren: %s :siren:\\n\\n\"}", noRecId, zeroId
        ));
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

}
