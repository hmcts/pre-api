package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.RoleDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClosePendingCasesTest {

    private CaseService caseService;
    private ClosePendingCases closePendingCasesTask;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private static final String ROBOT_USER_EMAIL = "example@example.com";

    @BeforeEach
    public void setUp() {
        caseService = mock(CaseService.class);
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        closePendingCasesTask = new ClosePendingCases(
            caseService,
            userService,
            userAuthenticationService,
            ROBOT_USER_EMAIL
        );
        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        var role = new RoleDTO();
        role.setName("Super User");
        appAccess.setRole(role);
        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuth));
    }

    @Test
    public void testRun() {
        closePendingCasesTask.run();

        verify(caseService, times(1)).closePendingCases();
    }
}
