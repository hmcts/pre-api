package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.*;

import static org.mockito.Mockito.*;

public class CheckForMissingRecordingsTest {

    private CheckForMissingRecordings checkForMissingRecordingsTask;
    private CaptureSessionService captureSessionService;
    private SlackClient slackClient;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private static final String ROBOT_USER_EMAIL = "example@example.com";

    @BeforeEach
    public void setUp() {
        String platformEnv = "Local-Testing";

        slackClient = mock(SlackClient.class);
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        captureSessionService = mock(CaptureSessionService.class);
        when(captureSessionService.findMissingRecordingIds(any())).thenReturn(Arrays.asList("123", "456"));

        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuth));

        checkForMissingRecordingsTask = new CheckForMissingRecordings(captureSessionService, slackClient, userService, userAuthenticationService, ROBOT_USER_EMAIL, platformEnv);
    }

    @Test
    public void testMissingRecordingsAreFound() {
        when(captureSessionService.findMissingRecordingIds(any())).thenReturn(Arrays.asList("123", "456"));
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findMissingRecordingIds(any());
        verify(slackClient, times(1)).postSlackMessage("{\"text\":\":globe_with_meridians: *Environment:* Local-Testing\\n\\n" +
                                                           ":warning: *Missing Recordings:*" +
                                                           "\\n\\n\\t:siren: 123 :siren:" +
                                                           "\\n\\t:siren: 456 :siren:\\n\\n\"}");
    }

    @Test
    public void testMissingRecordingsAreNotFound() {
        when(captureSessionService.findMissingRecordingIds(any())).thenReturn(Collections.emptyList());
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findMissingRecordingIds(any());
        verify(slackClient, times(0)).postSlackMessage(anyString());
    }

}
