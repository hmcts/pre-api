package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
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

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.DATE_TIME_FORMAT;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.XmlFields.CREATE_TIME;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.XmlFields.DISPLAY_NAME;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.XmlFields.FILE_SIZE;

/**
 * Service responsible for tracking and reporting the migration of items.
 * It maintains lists of successfully migrated items and failed items,
 * and provides functionality to write these items to CSV files for reporting purposes.
 */
@Service
public class MigrationTrackerService {
    private static final List<String> MIGRATED_ITEM_HEADERS = List.of(
        DISPLAY_NAME, "Case Reference", "Witness", "Defendant", "Scheduled For",
        "Case State", "Version", "File Name", "Duration", FILE_SIZE, "Date / Time migrated"
    );
    private static final List<String> TEST_FAILURE_HEADERS = List.of(
        DISPLAY_NAME, CREATE_TIME,"Filename", FILE_SIZE, "Migration Date / Time",
        "Duration Check Fail", "Duration (in seconds)", "Keyword Check Fail",
        "Keyword Found", "Test Pattern"
    );
    private static final List<String> FAILED_ITEM_HEADERS = List.of(
        "Reason for Failure", DISPLAY_NAME,CREATE_TIME,"Filename", FILE_SIZE, "Date / Time");

    private final Map<String, List<FailedItem>> categorizedFailures = new HashMap<>();
    private final List<PassItem> migratedItems = new ArrayList<>();
    private final List<TestItem> testFailures = new ArrayList<>();
    private final List<NotifyItem> notifyItems = new ArrayList<>();
    private final List<CreateInviteDTO> invitedUsers = new ArrayList<>();

    private final LoggingService loggingService;

    public MigrationTrackerService(final LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void addMigratedItem(PassItem item) {
        migratedItems.add(item);
    }

    public void addNotifyItem(NotifyItem item) {
        notifyItems.add(item);
    }

    public void addTestItem(TestItem item) {
        testFailures.add(item);
        loggingService.logInfo(
            "Adding test item: Category = %s | Filename = %s",
            "Test", item.getArchiveItem().getFileName()
        );
    }

    public void addFailedItem(FailedItem item) {
        categorizedFailures
            .computeIfAbsent(item.getFailureCategory(), k -> new ArrayList<>())
            .add(item);

        loggingService.logInfo(
            "Adding failed item: Category = %s | Filename = %s",
            item.getFailureCategory(), item.getFileName()
        );
    }

    public void addInvitedUser(CreateInviteDTO user) {
        invitedUsers.add(user);
    }

    public void writeMigratedItemsToCsv(String fileName, String outputDir) {
        List<List<String>> rows = buildMigratedItemsRows();
        try {
            ReportCsvWriter.writeToCsv(MIGRATED_ITEM_HEADERS, rows, fileName, outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write migrated items to CSV: %s", e.getMessage());
        }
    }

    public void writeNotifyItemsToCsv(String fileName, String outputDir) {
        List<String> headers = getNotifyItemsHeaders();
        List<List<String>> rows = buildNotifyItemsRows();
        try {
            ReportCsvWriter.writeToCsv(headers, rows, fileName, outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write notify items to CSV: %s", e.getMessage());
        }
    }

    public void writeTestFailureReport(String fileName,String outputDir) {
        List<List<String>> rows = buildTestFailureRows();

        try {
            ReportCsvWriter.writeToCsv(TEST_FAILURE_HEADERS, rows, fileName, outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write test failure report: %s", e.getMessage());
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
            List<List<String>> rows = buildFailedItemsRows(failedItemsList);

            try {
                ReportCsvWriter.writeToCsv(FAILED_ITEM_HEADERS, rows, fileName, outputDir, false);
            } catch (IOException e) {
                loggingService.logError("Failed to write failure report for %s: %s", category, e.getMessage());
            }
        });
    }

    public void writeInvitedUsersToCsv(String fileName, String outputDir) {
        List<String> headers = getInvitedUsersHeaders();
        List<List<String>> rows = buildInvitedUserRows();
        try {
            ReportCsvWriter.writeToCsv(headers, rows, fileName, outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write migrated items to CSV: %s", e.getMessage());
        }
    }

    /**
     * Writes both migrated and failed items to CSV files and logs the total counts,
     * and generates all reports at once.
     */
    public void writeAllToCsv() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
        String outputDir = "Migration Reports/" + timestamp;
        new File(outputDir).mkdirs();

        String failureDir = outputDir + "/Failure Reports";
        new File(failureDir).mkdirs();

        writeMigratedItemsToCsv("Migrated", outputDir);
        writeFailureSummary(outputDir);
        writeCategorizedFailureReports(failureDir);
        writeTestFailureReport("Test", failureDir);
        writeInvitedUsersToCsv("Invited_users", outputDir);
        writeNotifyItemsToCsv("Notify", outputDir);

        loggingService.setTotalMigrated(migratedItems.size());
        loggingService.setTotalFailed(categorizedFailures, testFailures);
        loggingService.setTotalInvited(invitedUsers.size());
        loggingService.logSummary();
    }

    // ==================================
    // Helpers
    // ==================================

    private List<String> getNotifyItemsHeaders() {
        return List.of(
            "Notification", DISPLAY_NAME, "Extracted_court", "Extracted_defendant",
            "Extracted_witness", "Date / Time migrated"
        );
    }

    private List<List<String>> buildMigratedItemsRows() {
        List<List<String>> rows = new ArrayList<>();
        for (PassItem item : migratedItems) {
            String migratedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));

            ExtractedMetadata extractedMetadata = item.item();
            ProcessedRecording cleansedData = item.cleansedData();
            rows.add(List.of(
                getValueOrEmpty(extractedMetadata.getArchiveName()),
                getValueOrEmpty(cleansedData.getCaseReference()),
                getValueOrEmpty(cleansedData.getWitnessFirstName()),
                getValueOrEmpty(cleansedData.getDefendantLastName()),
                getValueOrEmpty(cleansedData.getRecordingTimestamp()),
                getValueOrEmpty(cleansedData.getState()),
                getValueOrEmpty(cleansedData.getRecordingVersionNumber()),
                getValueOrEmpty(extractedMetadata.getFileName()),
                formatDuration(cleansedData.getDuration()),
                getValueOrEmpty(extractedMetadata.getFileSize()),
                migratedTime));
        }
        return rows;
    }

    private List<List<String>> buildNotifyItemsRows() {
        List<List<String>> rows = new ArrayList<>();
        for (NotifyItem item : notifyItems) {
            String migratedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));

            rows.add(List.of(
                    getValueOrEmpty(item.getNotification()),
                    getValueOrEmpty(item.getExtractedMetadata().getArchiveName()),
                    getValueOrEmpty(item.getExtractedMetadata().getCourtReference()),
                    getValueOrEmpty(item.getExtractedMetadata().getDefendantLastName()),
                    getValueOrEmpty(item.getExtractedMetadata().getWitnessFirstName()),
                    migratedTime
                )
            );
        }
        return rows;
    }

    private List<List<String>> buildTestFailureRows() {
        List<List<String>> rows = new ArrayList<>();
        for (TestItem item : testFailures) {
            CSVArchiveListData archiveItem = item.getArchiveItem();
            String failureTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));

            rows.add(List.of(
                getValueOrEmpty(archiveItem.getArchiveName()),
                getValueOrEmpty(archiveItem.getCreateTime()),
                getValueOrEmpty(archiveItem.getFileName()),
                getValueOrEmpty(archiveItem.getFileSize()) + " MB",
                failureTime,
                String.valueOf(item.isDurationCheck()),
                String.valueOf(item.getDurationInSeconds()),
                String.valueOf(item.isKeywordCheck()),
                getValueOrEmpty(item.getKeywordFound()),
                getValueOrEmpty(item.isRegexFailure())
            ));
        }
        return rows;
    }

    public List<List<String>> buildFailedItemsRows(List<FailedItem> items) {
        List<List<String>> rows = new ArrayList<>();

        for (FailedItem item : items) {
            Object itemData = item.getItem();
            String failureTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));

            if (itemData instanceof CSVArchiveListData archiveItem) {
                rows.add(List.of(
                    getValueOrEmpty(item.getReason()),
                    getValueOrEmpty(archiveItem.getArchiveName()),
                    getValueOrEmpty(archiveItem.getCreateTime()),
                    getValueOrEmpty(archiveItem.getFileName()),
                    getValueOrEmpty(archiveItem.getFileSize()),
                    failureTime
                ));
            } else if (itemData instanceof ExtractedMetadata metadata) {
                rows.add(List.of(
                    getValueOrEmpty(item.getReason()),
                    getValueOrEmpty(metadata.getArchiveName()),
                    getValueOrEmpty(metadata.getCreateTime() != null ? metadata.getCreateTime().toString() : ""),
                    getValueOrEmpty(metadata.getFileName()),
                    getValueOrEmpty(metadata.getFileSize()),
                    failureTime
                ));
            } else {
                loggingService.logWarning("Skipping unknown item type: %s", itemData.getClass().getSimpleName());
            }
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

        if (!testFailures.isEmpty()) {
            rows.add(List.of("Test Failures", String.valueOf(testFailures.size())));
        }

        try {
            ReportCsvWriter.writeToCsv(headers, rows, "Failures", outputDir, false);
        } catch (IOException e) {
            loggingService.logError("Failed to write failure summary: %s", e.getMessage());
        }
    }

    private List<String> getInvitedUsersHeaders() {
        return List.of("user_id", "First Name", "Last Name", "Email");
    }

    public List<List<String>> buildInvitedUserRows() {
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
