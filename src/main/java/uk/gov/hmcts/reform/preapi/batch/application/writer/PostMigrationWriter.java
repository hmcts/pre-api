package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.services.InviteService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.DATE_TIME_FORMAT;

@Component
public class PostMigrationWriter implements ItemWriter<PostMigratedItemGroup> {
    private final InviteService inviteService;
    private final ShareBookingService shareBookingService;
    private final LoggingService loggingService;
    private final MigrationTrackerService migrationTrackerService;

    public PostMigrationWriter(final InviteService inviteService,
                               final ShareBookingService shareBookingService,
                               final LoggingService loggingService,
                               final MigrationTrackerService migrationTrackerService) {
        this.inviteService = inviteService;
        this.shareBookingService = shareBookingService;
        this.loggingService = loggingService;
        this.migrationTrackerService = migrationTrackerService;
    }

    @Override
    public void write(Chunk<? extends PostMigratedItemGroup> items) {
        loggingService.logInfo("PostMigrationWriter triggered with %d item(s)", items.size());

        items.forEach(this::processMigratedItem);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processMigratedItem(PostMigratedItemGroup item) {
        loggingService.logDebug("Processing item group: %s", item);

        if (item.getInvites() != null) {
            for (CreateInviteDTO invite : item.getInvites()) {
                try {
                    inviteService.upsert(invite);
                    loggingService.logInfo("Invite created: %s", invite.getEmail());
                } catch (Exception e) {
                    loggingService.logError("Failed to create invite: %s | %s", invite.getEmail(), e);
                    recordFailure(
                        "Invite",
                        invite.getUserId() != null ? invite.getUserId().toString() : "",
                        invite.getEmail(),
                        "UPSERT",
                        e.getMessage()
                    );
                }
            }
        }

        if (item.getShareBookings() != null) {
            for (CreateShareBookingDTO share : item.getShareBookings()) {
                try {
                    shareBookingService.shareBookingById(share);
                    loggingService.logInfo("Share booking created: %s", share.getId());
                } catch (Exception e) {
                    loggingService.logError("Failed to create share booking: %s | %s", share.getId(), e);
                    recordFailure(
                        "ShareBooking",
                        share.getId() != null ? share.getId().toString() : "",
                        resolveEmailForShare(item, share),
                        "SHARE",
                        e.getMessage()
                    );
                }
            }
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
            return item.getInvites().stream()
                .filter(invite -> invite.getUserId() != null
                    && invite.getUserId().equals(share.getSharedWithUser()))
                .map(CreateInviteDTO::getEmail)
                .findFirst()
                .orElse("");
        }
        return "";
    }
}
