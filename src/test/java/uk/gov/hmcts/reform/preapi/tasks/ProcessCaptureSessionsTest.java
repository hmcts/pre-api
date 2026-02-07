package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ProcessingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ProcessCaptureSessions.class)
public class ProcessCaptureSessionsTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ProcessingService processingService;

    private ProcessCaptureSessions underTest;

    private static final String ROBOT_USER_EMAIL = "example@example.com";

    @BeforeEach
    void setUp() {
        underTest = new ProcessCaptureSessions(
            userService,
            userAuthenticationService,
            processingService,
            ROBOT_USER_EMAIL
        );

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
    public void shouldRunProcessingJob() {
        underTest.run();
        verify(processingService).processAllCaptureSessions();
    }

}
