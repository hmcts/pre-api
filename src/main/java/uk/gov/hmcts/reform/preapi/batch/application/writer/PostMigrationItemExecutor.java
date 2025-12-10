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
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.DATE_TIME_FORMAT;

@Component
public class PostMigrationItemExecutor {

    private final LoggingService loggingService;
    private final MigrationTrackerService migrationTrackerService;
    private final ShareBookingService shareBookingService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PortalAccessRepository portalAccessRepository;
    private final EmailServiceFactory emailServiceFactory;
    private final String vodafoneUserEmail;
    private final TransactionTemplate newTransactionTemplate;

    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String ENTITY_TYPE_SHARE_BOOKING = "ShareBooking";
    private static final String EMAIL_UNKNOWN = "Unknown";
    private static final String REASON_USER_INACTIVE_OR_DELETED = "User is inactive or deleted";
    private static final String REASON_SHARED_WITH_USER_NULL = "SharedWithUser is null";
    private static final String LOG_USER_DELETED = "User %s is deleted - skipping";
    private static final String LOG_USER_DELETED_PORTAL_ACCESS = "User %s has deleted portal access - skipping";
    private static final String LOG_USER_INACTIVE_PORTAL_ACCESS = "User %s has INACTIVE portal access - skipping";
    private static final String LOG_ERROR_CHECKING_USER_STATUS = "Error checking user status for %s: %s";

    @Autowired
    public PostMigrationItemExecutor(final LoggingService loggingService,
                                     final MigrationTrackerService migrationTrackerService,
                                     final ShareBookingService shareBookingService,
                                     final UserService userService,
                                     final UserRepository userRepository,
                                     final PortalAccessRepository portalAccessRepository,
                                     final EmailServiceFactory emailServiceFactory,
                                     final PlatformTransactionManager transactionManager,
                                     @Value("${vodafone-user-email}") String vodafoneUserEmail) {
        this.loggingService = loggingService;
        this.migrationTrackerService = migrationTrackerService;
        this.shareBookingService = shareBookingService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.portalAccessRepository = portalAccessRepository;
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
        if (invite.getUserId() != null) {
            if (!isUserActiveForMigration(invite.getUserId(), invite.getEmail())) {
                loggingService.logWarning("Skipping invite for inactive/deleted user: %s", invite.getEmail());
                recordSkippedInvite(invite, REASON_USER_INACTIVE_OR_DELETED);
                return;
            }

            var existingInvite = portalAccessRepository
                .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(
                    invite.getUserId(), 
                    AccessStatus.INVITATION_SENT
                );
            
            if (existingInvite.isPresent()) {
                loggingService.logDebug("Skipping invite for user %s — invite already exists", invite.getEmail());
                migrationTrackerService.addInvitedUser(invite);
                return;
            }
        }
        
        try {
            newTransactionTemplate.executeWithoutResult(status -> userService.upsert(invite));

            loggingService.logInfo("User created: %s", invite.getEmail());
            migrationTrackerService.addInvitedUser(invite);
            sendPortalInvite(invite);
        } catch (Exception e) {
            loggingService.logError("Failed to create user: %s | %s", invite.getEmail(), e.getMessage());
            recordFailedInvite(invite, e);
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
        
        if (share.getSharedWithUser() != null) {
            if (email.isEmpty()) {
                try {
                    email = userService.findById(share.getSharedWithUser()).getEmail();
                } catch (Exception e) {
                    loggingService.logWarning("Could not resolve email for user ID: %s - %s", 
                        share.getSharedWithUser(), e.getMessage());
                    email = "unknown@" + share.getSharedWithUser().toString().substring(0, 8);
                }
            }
            
            if (!isUserActiveForMigration(share.getSharedWithUser(), email)) {
                loggingService.logWarning("Skipping share booking for inactive/deleted user: %s (ID: %s)", 
                    email, share.getSharedWithUser());
                recordSkippedShareBooking(share, email, REASON_USER_INACTIVE_OR_DELETED);
                return; 
            }
        } else {
            loggingService.logWarning("Skipping share booking - sharedWithUser is null for share: %s", 
                safeIdToString(share.getId()));
            recordSkippedShareBooking(share, email, REASON_SHARED_WITH_USER_NULL);
            return; 
        }
        
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
            recordFailedShareBooking(share, email, e);
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
                var user = userService.findById(share.getSharedWithUser());
                return user.getEmail();
            } catch (NotFoundException e) {
                loggingService.logWarning(
                    "User not found for ID: %s - %s", share.getSharedWithUser(), e.getMessage());
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

    private boolean isUserActiveForMigration(UUID userId, String email) {
        try {
            var user = userService.findById(userId);
            if (user.getDeletedAt() != null) {
                loggingService.logDebug(LOG_USER_DELETED, email);
                return false;
            }
            
            var portalAccess = portalAccessRepository
                .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId);
            
            if (portalAccess.isEmpty()) {
                var deletedPortalAccess = portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId);
                if (!deletedPortalAccess.isEmpty()) {
                    loggingService.logDebug(LOG_USER_DELETED_PORTAL_ACCESS, email);
                    return false;
                }
                return true;
            }
            
            if (portalAccess.get().getStatus() == AccessStatus.INACTIVE) {
                loggingService.logDebug(LOG_USER_INACTIVE_PORTAL_ACCESS, email);
                return false;
            }
            
            return true;
        } catch (NotFoundException e) {
            loggingService.logDebug("User %s does not exist yet - will create", email);
            return true;  
        } catch (Exception e) {
            loggingService.logWarning(LOG_ERROR_CHECKING_USER_STATUS, email, e.getMessage());
            return false;
        }
    }

    private String safeIdToString(UUID id) {
        return id != null ? id.toString() : "";
    }

    private String safeEmail(String email) {
        return email.isEmpty() ? EMAIL_UNKNOWN : email;
    }

    private void recordSkippedShareBooking(CreateShareBookingDTO share, String email, String reason) {
        recordFailure(
            ENTITY_TYPE_SHARE_BOOKING,
            safeIdToString(share.getId()),
            safeEmail(email),
            STATUS_SKIPPED,
            reason
        );
    }

    private void recordFailedShareBooking(CreateShareBookingDTO share, String email, Exception e) {
        recordFailure(
            ENTITY_TYPE_SHARE_BOOKING,
            safeIdToString(share.getId()),
            safeEmail(email),
            "SHARE",
            extractReason(e)
        );
    }

    private void recordSkippedInvite(CreateInviteDTO invite, String reason) {
        recordFailure(
            "Invite",
            invite.getUserId() != null ? invite.getUserId().toString() : "",
            invite.getEmail(),
            STATUS_SKIPPED,
            reason
        );
    }

    private void recordFailedInvite(CreateInviteDTO invite, Exception e) {
        recordFailure(
            "Invite",
            invite.getUserId() != null ? invite.getUserId().toString() : "",
            invite.getEmail(),
            "USER_CREATION",
            extractReason(e)
        );
    }
}
