package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.ReportingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for tracking and reporting the migration of items.
 * It maintains lists of successfully migrated items and failed items,
 * and provides functionality to write these items to CSV files for reporting purposes.
 */
@Service
public class MigrationTrackerService {
    private final Map<String, List<FailedItem>> categorizedFailures = new HashMap<>();
    private List<PassItem> migratedItems = new ArrayList<>();
    private List<CreateInviteDTO> invitedUsers = new ArrayList<>();
    private final ReportingService reportingService;
    private LoggingService loggingService;

    public MigrationTrackerService(
        ReportingService reportingService,
        LoggingService loggingService
    ) {
        this.reportingService = reportingService;
        this.loggingService = loggingService;
    }

    public void addMigratedItem(PassItem item) {
        migratedItems.add(item);
    }

    public void addFailedItem(FailedItem item) {
        categorizedFailures
            .computeIfAbsent(item.getFailureCategory(), k -> new ArrayList<>())
            .add(item);

        loggingService.logInfo(
            "Added failed item: Category = %s | Filename = %s",
            item.getFailureCategory(), item.getArchiveItem().getFileName()
        );
    }

    public void addInvitedUser(CreateInviteDTO user) {
        invitedUsers.add(user);
    }

    public void writeMigratedItemsToCsv(String fileName, String outputDir) {
        List<String> headers = getMigratedItemsHeaders();
        List<List<String>> rows = buildMigratedItemsRows(migratedItems);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write migrated items to CSV: %s", e.getMessage());
        }
    }

    public void writeCategorizedFailureReports(String outputDir) {
        categorizedFailures.forEach((category, failedItemsList) -> {
            loggingService.logInfo(
                "Writing failure report for category: %s | %d items",
                category,
                failedItemsList.size()
            );

            String fileName = sanitizeFileName(category);
            List<String> headers = getFailedItemsHeaders();
            List<List<String>> rows = buildFailedItemsRows(failedItemsList);

            loggingService.logInfo("Rows built for %s: %d", category, rows.size());

            try {
                reportingService.writeToCsv(headers, rows, fileName, outputDir, false);
            } catch (IOException e) {
                loggingService.logError("Failed to write failure report for %s: %s", category, e.getMessage());
            }
        });
    }


    public void writeInvitedUsersToCsv(String fileName, String outputDir) {
        List<String> headers = getInvitedUsersHeaders();
        List<List<String>> rows = buildInvitedUserRows(invitedUsers);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write migrated items to CSV: %s", e.getMessage());
        }
    }

    /**
     * Writes both migrated and failed items to CSV files and logs the total counts,
     * and generates all reports at once.
     */
    public void writeAllToCsv() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));
        String outputDir = "Migration Reports/" + timestamp;
        new File(outputDir).mkdirs();

        String failureDir = outputDir + "/Failure Reports";
        new File(failureDir).mkdirs();

        writeMigratedItemsToCsv("Migrated", outputDir);
        writeFailureSummary(outputDir);
        writeCategorizedFailureReports(failureDir);
        writeInvitedUsersToCsv("Invited_users", outputDir);

        loggingService.setTotalMigrated(migratedItems.size());
        loggingService.setTotalFailed(categorizedFailures);
        loggingService.setTotalInvited(invitedUsers.size());
        loggingService.logSummary();
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
        return List.of("Reason for Failure", "Display Name", "Filename", "File Size", "Date / Time");
    }

    public List<List<String>> buildFailedItemsRows(List<FailedItem> items) {
        List<List<String>> rows = new ArrayList<>();
        loggingService.logDebug("Building rows for failed items: %d items", items.size());

        for (FailedItem item : items) {
            CSVArchiveListData archiveItem = item.getArchiveItem();
            String failureTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            List<String> row = List.of(
                getValueOrEmpty(item.getReason()),
                getValueOrEmpty(archiveItem.getArchiveName()),
                getValueOrEmpty(archiveItem.getFileName()),
                getValueOrEmpty(archiveItem.getFileSize()),
                failureTime
            );

            loggingService.logDebug("Adding row: %s", row);
            rows.add(row);
        }
        return rows;
    }

    private void writeFailureSummary(String outputDir) {
        List<String> headers = List.of("Failure Category", "Count");
        List<List<String>> rows = categorizedFailures.entrySet().stream()
                                                     .map(entry -> List.of(
                                                         entry.getKey(),
                                                         String.valueOf(entry.getValue().size())
                                                     ))
                                                     .collect(Collectors.toList());

        try {
            reportingService.writeToCsv(headers, rows, "Failures", outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write failure summary: %s", e.getMessage());
        }
    }

    private List<String> getInvitedUsersHeaders() {
        return List.of("user_id", "First Name", "Last Name", "Email");
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

    private String sanitizeFileName(String category) {
        return category.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

}
