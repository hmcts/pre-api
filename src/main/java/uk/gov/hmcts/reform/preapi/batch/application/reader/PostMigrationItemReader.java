package uk.gov.hmcts.reform.preapi.batch.application.reader;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.services.BookingService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PostMigrationItemReader {

    private final MigrationRecordService migrationRecordService;
    private final InMemoryCacheService cacheService;
    private final BookingService bookingService;
    private final EntityCreationService entityCreationService;
    private final MigrationTrackerService migrationTrackerService;
    private final LoggingService loggingService;
    private final String vodafoneUserEmail;

    @Autowired
    public PostMigrationItemReader(final MigrationRecordService migrationRecordService,
                                   final InMemoryCacheService cacheService,
                                   final BookingService bookingService,
                                   final EntityCreationService entityCreationService,
                                   final MigrationTrackerService migrationTrackerService,
                                   final LoggingService loggingService,
                                   @Value("${vodafone.user.email:}") String vodafoneUserEmail) {
        this.migrationRecordService = migrationRecordService;
        this.cacheService = cacheService;
        this.bookingService = bookingService;
        this.entityCreationService = entityCreationService;
        this.migrationTrackerService = migrationTrackerService;
        this.loggingService = loggingService;
        this.vodafoneUserEmail = vodafoneUserEmail;
    }

    public ItemReader<PostMigratedItemGroup> createReader(boolean dryRun) {
        List<PostMigratedItemGroup> migratedItems = new ArrayList<>();

        // fetch SUCCESS ORIGs with booking + group key
        List<MigrationRecord> shareableOrigs = migrationRecordService.findShareableOrigs();
        Map<String, List<String[]>> channelUsersMap = cacheService.getAllChannelReferences();

        for (MigrationRecord orig : shareableOrigs) {
            loggingService.logDebug("========================================================");
            loggingService.logDebug("Processing record: archiveId=%s, groupKey=%s",
                orig.getArchiveId(), orig.getRecordingGroupKey());

            // find matching channel users by checking the groupKey parts
            List<String[]> matchedUsers = channelUsersMap.entrySet().stream()
                .filter(entry -> channelContainsAllGroupParts(orig.getRecordingGroupKey(), entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .toList();

            if (matchedUsers.isEmpty()) {
                loggingService.logDebug("No matching channel users found for groupKey=%s",
                    orig.getRecordingGroupKey());
                continue;
            }

            if (orig.getBookingId() == null) {
                loggingService.logWarning("Record %s has no bookingId", orig.getArchiveId());
                continue;
            }

            // fetch booking 
            BookingDTO booking;
            try {
                booking = bookingService.findById(orig.getBookingId()); 
            } catch (Exception ex) {
                loggingService.logWarning("No booking found for record %s (bookingId=%s) — %s",
                    orig.getArchiveId(), orig.getBookingId(), ex.getMessage());
                continue;
            }
            if (booking == null) {
                loggingService.logWarning("No booking found for record %s (bookingId=%s)",
                    orig.getArchiveId(), orig.getBookingId());
                continue;
            }

            var alreadySharedEmails = booking.getShares() == null ? new HashSet<String>() 
                : booking.getShares().stream()
                .filter(share -> share.getDeletedAt() == null && share.getSharedWithUser() != null)
                .map(share -> share.getSharedWithUser().getEmail().toLowerCase())
                .collect(Collectors.toCollection(HashSet::new));

            for (String[] user : matchedUsers) {
                String email = user[1];
                String fullName = user[0];
                String[] nameParts = fullName.split("\\.");
                String firstName = nameParts.length > 0 ? nameParts[0] : "Unknown";
                String lastName  = nameParts.length > 1 ? nameParts[1] : "Unknown";

                String emailKey = email.toLowerCase();

                if (alreadySharedEmails.contains(emailKey)) {
                    loggingService.logDebug("Skipping share creation for %s — already shared.", email);
                    continue;
                }

                if (dryRun) {
                    loggingService.logInfo("[DRY RUN] Would invite and share booking with %s", email);
                    alreadySharedEmails.add(emailKey);
                    continue;
                }

                var result = entityCreationService.prepareShareBookingAndInviteData(
                    booking, email, firstName, lastName
                );

                if (result != null) {
                    migratedItems.add(result);
                    alreadySharedEmails.add(emailKey);
                    loggingService.logDebug("Prepared data for user: %s", email);
                }
            }
        }

        loggingService.logInfo("PostMigrationItemReader prepared %d items for processing", migratedItems.size());
        return new ListItemReader<>(migratedItems);
    }

    private static boolean channelContainsAllGroupParts(String recordingGroupKey, String channelName) {
        if (recordingGroupKey == null || channelName == null) {
            return false;
        }

        String lowerChannel = channelName.toLowerCase();

        for (String rawPart : recordingGroupKey.split("\\|")) {
            if (rawPart == null || rawPart.isBlank()) {
                continue;
            }

            String part = rawPart.toLowerCase().trim();

            if (part.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String yymmdd =
                    part.substring(2, 4) + part.substring(5, 7) + part.substring(8, 10);

                if (lowerChannel.contains(part) || lowerChannel.contains(yymmdd)) {
                    continue; 
                }
                return false;
            }

            if (!lowerChannel.contains(part)) {
                return false;
            }
        }

        return true;
    }
}
