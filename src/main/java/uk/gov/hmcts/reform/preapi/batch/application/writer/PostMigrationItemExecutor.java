package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.DATE_TIME_FORMAT;

@Component
public class PostMigrationItemExecutor {

    private final LoggingService loggingService;
    private final MigrationTrackerService migrationTrackerService;
    private final ShareBookingService shareBookingService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EmailServiceFactory emailServiceFactory;
    private final String vodafoneUserEmail;
    private final TransactionTemplate newTransactionTemplate;

    @Autowired
    public PostMigrationItemExecutor(final LoggingService loggingService,
                                     final MigrationTrackerService migrationTrackerService,
                                     final ShareBookingService shareBookingService,
                                     final UserService userService,
                                     final UserRepository userRepository,
                                     final EmailServiceFactory emailServiceFactory,
                                     final PlatformTransactionManager transactionManager,
                                     @Value("${vodafone-user-email}") String vodafoneUserEmail) {
        this.loggingService = loggingService;
        this.migrationTrackerService = migrationTrackerService;
        this.shareBookingService = shareBookingService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.emailServiceFactory = emailServiceFactory;
        this.vodafoneUserEmail = vodafoneUserEmail;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean processOneItem(PostMigratedItemGroup item) {
        loggingService.logDebug("Processing item group: %s", item);

        if (item.getInvites() != null) {
            for (CreateInviteDTO invite : item.getInvites()) {
                handleInvite(invite);
            }
        }

        if (item.getShareBookings() != null) {
            for (CreateShareBookingDTO share : item.getShareBookings()) {
                handleShare(item, share);
            }
        }

        return true;
    }

    private void handleInvite(CreateInviteDTO invite) {
        try {
            newTransactionTemplate.executeWithoutResult(status -> userService.upsert(invite));

            loggingService.logInfo("User created: %s", invite.getEmail());
            migrationTrackerService.addInvitedUser(invite);
            sendPortalInvite(invite);
        } catch (Exception e) {
            loggingService.logError("Failed to create user: %s | %s", invite.getEmail(), e.getMessage());
            recordFailure(
                "Invite",
                invite.getUserId() != null ? invite.getUserId().toString() : "",
                invite.getEmail(),
                "USER_CREATION",
                extractReason(e)
            );
        }
    }

    private void sendPortalInvite(CreateInviteDTO invite) {
        try {
            var user = userRepository.findById(invite.getUserId())
                .orElseThrow(() -> new NotFoundException("User: " + invite.getUserId()));

            if (emailServiceFactory.isEnabled()) {
                emailServiceFactory.getEnabledEmailService().portalInvite(user);
                loggingService.logInfo("Portal invite sent to: %s", invite.getEmail());
            } else {
                loggingService.logInfo("Email service disabled - skipping portal invite for: %s", invite.getEmail());
            }
        } catch (Exception emailException) {
            loggingService.logError("Failed to send portal invite for user: %s | %s",
                invite.getEmail(), emailException.getMessage());
        }
    }

    private void handleShare(PostMigratedItemGroup item, CreateShareBookingDTO share) {
        String email = resolveEmailForShare(item, share);
        try {
            newTransactionTemplate.executeWithoutResult(status -> shareBookingService.shareBookingById(share));

            loggingService.logInfo("Share booking created: %s", share.getId());
            migrationTrackerService.addShareBooking(share);
            migrationTrackerService.addShareBookingReport(
                share,
                email,
                vodafoneUserEmail
            );
        } catch (Exception e) {
            loggingService.logError("Failed to create share booking: %s | %s", share.getId(), e.getMessage());
            recordFailure(
                "ShareBooking",
                share.getId() != null ? share.getId().toString() : "",
                email,
                "SHARE",
                extractReason(e)
            );
        }
    }


    private void recordFailure(String entityType, String identifier, String email, String action, String reason) {
        migrationTrackerService.addShareInviteFailure(new MigrationTrackerService.ShareInviteFailureEntry(
            entityType,
            identifier,
            email,
            action,
            reason,
            DateTimeFormatter.ofPattern(DATE_TIME_FORMAT).format(LocalDateTime.now())
        ));
    }

    private String resolveEmailForShare(PostMigratedItemGroup item, CreateShareBookingDTO share) {
        if (item.getInvites() != null) {
            String email = item.getInvites().stream()
                .filter(invite -> invite.getUserId() != null
                    && invite.getUserId().equals(share.getSharedWithUser()))
                .map(CreateInviteDTO::getEmail)
                .findFirst()
                .orElse("");
            if (!email.isEmpty()) {
                return email;
            }
        }
        
        if (share.getSharedWithUser() != null) {
            try {
                return userService.findById(share.getSharedWithUser()).getEmail();
            } catch (Exception e) {
                loggingService.logWarning(
                    "Could not find user email for ID: %s - %s", share.getSharedWithUser(), e.getMessage());
            }
        }
        return "";
    }

    private String extractReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }
}
