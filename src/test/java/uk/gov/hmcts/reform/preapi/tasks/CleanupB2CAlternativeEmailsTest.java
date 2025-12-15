package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.B2CGraphService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanupB2CAlternativeEmailsTest {

    @Mock
    private UserService userService;

    @Mock
    private UserAuthenticationService userAuthenticationService;

    @Mock
    private B2CGraphService b2cGraphService;

    @Test
    void run_skips_users_with_null_alternative_email_and_does_not_update_local_user() {
        // Prepare a single UserDTO with null alternativeEmail
        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("primary@cjsm.net");
        user.setAlternativeEmail(null);

        // stub repository paging to return one page with the single user
        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        // create task but override signInRobotUser to skip authentication
        var task = new CleanupB2CAlternativeEmails(userService, userAuthenticationService,
            "cron@local", b2cGraphService) {
            @Override
            protected void signInRobotUser() {
                // no-op for tests
            }
        };

        // run the task
        task.run();

        // Because the user's alternativeEmail is null, the task should not call updateAlternativeEmail
        verify(userService, never()).updateAlternativeEmail(any(), any());
    }
}
