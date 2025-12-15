package uk.gov.hmcts.reform.preapi.tasks;

import com.microsoft.graph.models.ObjectIdentity;
import com.microsoft.graph.models.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CleanupB2CAlternativeEmails extends RobotUserTask {

    private final B2CGraphService b2cGraphService;

    @Autowired
    protected CleanupB2CAlternativeEmails(UserService userService,
                                          UserAuthenticationService userAuthenticationService,
                                          @Value("${cron-user-email}") String cronUserEmail,
                                          B2CGraphService b2cGraphService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.b2cGraphService = b2cGraphService;
    }

    @Override
    public void run() {
        signInRobotUser();

        log.info("Starting CleanupB2CAlternativeEmails task");
        var pageSize = 50;
        var pageNumber = 0;
        var pageable = Pageable.ofSize(pageSize);

        var page = userService.findPortalUsersWithCjsmEmail(pageable);
        var totalUsers = page.getTotalElements();
        log.info("Found {} users with CJSM email addresses to process", totalUsers);

        while (!page.isEmpty()) {
            log.info("Processing page {} of {} ({} users)",
                     pageNumber + 1, page.getTotalPages(), page.getNumberOfElements());

            page.forEach(this::removeB2CAlternativeEmail);

            if (page.hasNext()) {
                pageNumber++;
                pageable = Pageable.ofSize(pageSize).withPage(pageNumber);
                page = userService.findPortalUsersWithCjsmEmail(pageable);
            } else {
                break;
            }
        }

        log.info("Completed CleanupB2CAlternativeEmails task");
    }

    private void removeB2CAlternativeEmail(UserDTO user) {
        var primaryEmail = user.getEmail();
        var alternativeEmail = user.getAlternativeEmail();
        try {
            log.info("Processing user with primary email: {}, alternative email: {}", primaryEmail, alternativeEmail);

            if (alternativeEmail == null || alternativeEmail.trim().isEmpty()) {
                log.warn("User {} has no alternative email configured, skipping", primaryEmail);
                return;
            }

            var maybeB2cUser = b2cGraphService.findUserByPrimaryEmail(primaryEmail);
            if (maybeB2cUser.isEmpty()) {
                log.warn("No B2C user found with email: {}", primaryEmail);
                return;
            }

            User b2cUser = maybeB2cUser.get();
            List<ObjectIdentity> identities = b2cUser.getIdentities();

            if (identities == null || identities.isEmpty()) {
                log.warn("User {} has no identities", b2cUser.getId());
                return;
            }

            // Filter out the alternative email identity
            List<ObjectIdentity> updatedIdentities = new ArrayList<>();
            boolean found = false;

            for (ObjectIdentity identity : identities) {
                String issuerAssignedId = identity.getIssuerAssignedId();
                if (issuerAssignedId != null && issuerAssignedId.equalsIgnoreCase(alternativeEmail)) {
                    found = true;
                    log.info("Removing alternative email identity: {}", alternativeEmail);
                } else {
                    updatedIdentities.add(identity);
                }
            }

            if (!found) {
                log.warn("Alternative email {} not found in user identities", alternativeEmail);
                return;
            }

            // Update user with filtered identities via the graph service
            b2cGraphService.updateUserIdentities(b2cUser.getId(), updatedIdentities);

            log.info("Successfully removed alternative email {} from user {}", alternativeEmail, primaryEmail);

            // Persist the change locally so the user isn't picked up again
            try {
                userService.updateAlternativeEmail(user.getId(), null);
                // Also update the DTO so the in-memory object reflects the persisted state
                user.setAlternativeEmail(null);
                log.info("Local user {} updated: alternativeEmail set to null", primaryEmail);
            } catch (Exception e) {
                log.error(
                    "Failed to update local user {} alternative email to null: {}", primaryEmail, e.getMessage(), e
                );
            }

        } catch (Exception e) {
            log.error("Failed to remove alternative email {} for user {}: {}",
                     alternativeEmail, primaryEmail, e.getMessage(), e);
        }
    }
}
