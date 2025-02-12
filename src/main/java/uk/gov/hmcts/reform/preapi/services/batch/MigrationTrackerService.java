package uk.gov.hmcts.reform.preapi.services.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for tracking and reporting the migration of items.
 * It maintains lists of successfully migrated items and failed items,
 * and provides functionality to write these items to CSV files for reporting purposes.
 */
@Service
public class MigrationTrackerService {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    private List<PassItem> migratedItems = new ArrayList<>();
    private List<FailedItem> failedItems = new ArrayList<>();
    private List<CreateInviteDTO> invitedUsers = new ArrayList<>();
    private final ReportingService reportingService;

    public MigrationTrackerService(ReportingService reportingService) {
        this.reportingService = reportingService;
    }
    
    public void addMigratedItem(PassItem item) {
        migratedItems.add(item);
    }

    public void addFailedItem(FailedItem item) {
        failedItems.add(item);
    }

    public void addInvitedUser(CreateInviteDTO user) {
        invitedUsers.add(user);
    }

    public void writeMigratedItemsToCsv(String fileName, String outputDir)  {
        List<String> headers = getMigratedItemsHeaders();
        List<List<String>> rows = buildMigratedItemsRows(migratedItems);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, true);
        } catch (IOException e) {
            logger.error("Failed to write migrated items to CSV: {}", e.getMessage());
        }
    }

    public void writeFailedItemsToCsv(String fileName, String outputDir) {
        List<String> headers = getFailedItemsHeaders();
        List<List<String>> rows = buildFailedItemsRows(failedItems);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, true);
        } catch (IOException e) {
            logger.error("Failed to write migrated items to CSV: {}", e.getMessage());
        }
    }

    public void writeInvitedUsersToCsv(String fileName, String outputDir) {
        List<String> headers = getInvitedUsersHeaders();
        List<List<String>> rows = buildInvitedUserRows(invitedUsers);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, true);
        } catch (IOException e) {
            logger.error("Failed to write migrated items to CSV: {}", e.getMessage());
        }
    }

    /**
     * Writes both migrated and failed items to CSV files and logs the total counts,
     * and generates all reports at once.
     */
    public void writeAllToCsv() {
        writeMigratedItemsToCsv("Migrated","Migration Reports");
        writeFailedItemsToCsv("Failed","Migration Reports");
        writeInvitedUsersToCsv("Invited_users","Migration Reports");
        logger.info("Total Migrated Items: {}", migratedItems.size());
        logger.info("Total Failed Items: {}", failedItems.size());
        logger.info("Total Invited Items: {}", invitedUsers.size());
    }

    // ==================================
    // Helpers
    // ==================================

    private List<String> getMigratedItemsHeaders() {
        return List.of(
            "Regex Pattern", "Display Name", "Case Reference", "Scheduled For",
            "Case State", "Version", "File Name", "Duration", "File Size",
            "Date / Time migrated"
        );
    }

    private List<List<String>> buildMigratedItemsRows(List<PassItem> items) {
        List<List<String>> rows = new ArrayList<>();
        for (PassItem item : migratedItems) {
            String migratedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            rows.add(List.of(
                getValueOrEmpty(item.getRegexPattern()), 
                getValueOrEmpty(item.getArchiveName()),
                getValueOrEmpty(item.getCaseReference()), 
                getValueOrEmpty(item.getScheduledFor()),
                getValueOrEmpty(item.getState()), 
                getValueOrEmpty(item.getVersion()), 
                getValueOrEmpty(item.getFileName()),   
                formatDuration(item.getDuration()),
                getValueOrEmpty(item.getFileSize()),
                migratedTime
                )
            );
        }
        return rows;
    }

    private List<String> getFailedItemsHeaders() {
        return List.of("Reason for Failure", "Display Name","Filename","File Size", "Date / Time");
    }

    public List<List<String>> buildFailedItemsRows(List<FailedItem> items) {
        List<List<String>> rows = new ArrayList<>();

        for (FailedItem item : failedItems) {
            CSVArchiveListData archiveItem = item.getArchiveItem();
            String failureTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            rows.add(List.of(
                getValueOrEmpty(item.getReason()), 
                getValueOrEmpty(archiveItem.getArchiveName()),
                getValueOrEmpty(archiveItem.getFileName()),
                getValueOrEmpty(archiveItem.getFileSize()),
                failureTime
                )
            );
        }
        return rows;
    }

    private List<String> getInvitedUsersHeaders() {
        return List.of("user_id","First Name", "Last Name","Email");
    }

    public List<List<String>> buildInvitedUserRows(List<CreateInviteDTO> items) {
        List<List<String>> rows = new ArrayList<>();

        for (CreateInviteDTO item : invitedUsers) {

            rows.add(List.of(
                getValueOrEmpty(item.getUserId()), 
                getValueOrEmpty(item.getFirstName()),
                getValueOrEmpty(item.getLastName()),
                getValueOrEmpty(item.getEmail())
                )
            );
        }
        return rows;
    }


    private String getValueOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}