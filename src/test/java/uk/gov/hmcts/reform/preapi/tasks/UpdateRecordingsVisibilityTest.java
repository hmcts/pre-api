package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UpdateRecordingsVisibility.class)
class UpdateRecordingsVisibilityTest {

    private static final String CRON_USER_EMAIL = "cron@example.com";

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private User user;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @BeforeEach
    void setUp() {
        BaseAppAccessDTO appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());

        AccessDTO access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(mock(UserAuthentication.class)));
    }

    @Test
    @DisplayName("Undelete originals if Hide Reencodes flag is set to true")
    void undeleteOriginalsIfHideReencodesFlagIsSetToTrue() {
        UpdateRecordingsVisibility underTest = new UpdateRecordingsVisibility(recordingService,
                                                                              userService,
                                                                              userAuthenticationService,
                                                                              CRON_USER_EMAIL,
                                                                              "true");
        underTest.run();

        verify(userService, times(1)).findByEmail(CRON_USER_EMAIL);
        verify(userAuthenticationService, times(1)).validateUser(any());

        verify(recordingService, times(1)).undeleteOriginalWhereReencodedVersionExists();

        verifyNoMoreInteractions(recordingService);
    }

    @Test
    @DisplayName("Delete original VF recordings where re-encoded version exists")
    void deleteVFWhereReEncodedVersionExists() {
        UpdateRecordingsVisibility underTest = new UpdateRecordingsVisibility(recordingService,
                                                                              userService,
                                                                              userAuthenticationService,
                                                                              CRON_USER_EMAIL,
                                                                              "false");

        underTest.run();

        verify(userService, times(1)).findByEmail(CRON_USER_EMAIL);
        verify(userAuthenticationService, times(1)).validateUser(any());

        verify(recordingService, times(1)).deleteOriginalWhereReencodedVersionExists();

        verifyNoMoreInteractions(recordingService);
    }

}
