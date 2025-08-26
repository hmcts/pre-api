package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.services.InviteService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

@Component
@Transactional(propagation = Propagation.REQUIRED)
public class PostMigrationWriter implements ItemWriter<PostMigratedItemGroup> {
    private final InviteService inviteService;
    private final ShareBookingService shareBookingService;
    private final LoggingService loggingService;

    public PostMigrationWriter(final InviteService inviteService,
                               final ShareBookingService shareBookingService,
                               final LoggingService loggingService) {
        this.inviteService = inviteService;
        this.shareBookingService = shareBookingService;
        this.loggingService = loggingService;
    }

    @Override
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void write(Chunk<? extends PostMigratedItemGroup> items) {
        loggingService.logInfo("PostMigrationWriter triggered with %d item(s)", items.size());

        for (PostMigratedItemGroup item : items) {
            loggingService.logDebug("Processing item group: %s", item);

            if (item.getInvites() != null) {
                for (CreateInviteDTO invite : item.getInvites()) {
                    try {
                        inviteService.upsert(invite);
                        loggingService.logInfo("Invite created: %s", invite.getEmail());
                    } catch (Exception e) {
                        loggingService.logError("Failed to create invite: %s | %s", invite.getEmail(), e);
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
                    }
                }
            }
        }
    }
}

