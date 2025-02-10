package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service responsible for tracking and reporting the migration of items.
 * It maintains lists of successfully migrated items and failed items,
 * and provides functionality to write these items to CSV files for reporting purposes.
 */
@Service
public class MigrationTrackerService {

    private List<PassItem> migratedItems = new ArrayList<>();
    private List<FailedItem> failedItems = new ArrayList<>();
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

    public void writeMigratedItemsToCsv(String fileName, String outputDir)  {
        List<String> headers = getMigratedItemsHeaders();
        List<List<String>> rows = buildMigratedItemsRows(migratedItems);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, true);
        } catch (IOException e) {
            Logger.getAnonymousLogger().warning("Failed to write migrated items to CSV: " + e.getMessage());
        }
    }

    public void writeFailedItemsToCsv(String fileName, String outputDir) {
        List<String> headers = getFailedItemsHeaders();
        List<List<String>> rows = buildFailedItemsRows(failedItems);
        try {
            reportingService.writeToCsv(headers, rows, fileName, outputDir, true);
        } catch (IOException e) {
            Logger.getAnonymousLogger().warning("Failed to write migrated items to CSV: " + e.getMessage());
        }
    }


    /**
     * Writes both migrated and failed items to CSV files and logs the total counts,
     * and generates all reports at once.
     */
    public void writeAllToCsv() {
        writeMigratedItemsToCsv("Migrated","Migration Reports");
        writeFailedItemsToCsv("Failed","Migration Reports");
        Logger.getAnonymousLogger().info("Total Migrated Items: " + migratedItems.size());
        Logger.getAnonymousLogger().info("Total Failed Items: " + failedItems.size());

    }

    // ==================================
    // Helpers
    // ==================================

    private List<String> getMigratedItemsHeaders() {
        return List.of(
            "regexPattern", "archiveName", "caseReference", "isTest", "scheduledFor", "origin",
            "ingestAddress", "liveOutputURL", "startedAt", "startedByUserId", "finishedAt",
            "finishedByUserId", "status", "version", "fileName", "duration", "participants",
            "users", "shareBookings", "caseId", "courtId", "bookingId", "captureSessionId",
            "recordingId", "parentRecordingId", "participantIds", "shareBookingIds", "userIds"
        );
    }

    private List<List<String>> buildMigratedItemsRows(List<PassItem> items) {
        List<List<String>> rows = new ArrayList<>();
        for (PassItem item : migratedItems) {

            rows.add(List.of(
                getValueOrEmpty(item.getRegexPattern()), 
                getValueOrEmpty(item.getArchiveName()),
                getValueOrEmpty(item.getCaseReference()), 
                getValueOrEmpty(item.getIsTest()),
                getValueOrEmpty(item.getScheduledFor()),
                getValueOrEmpty(item.getOrigin()), 
                getValueOrEmpty(item.getIngestAddress()),
                getValueOrEmpty(item.getLiveOutputURL()),
                getValueOrEmpty(item.getStartedAt()),
                getValueOrEmpty(item.getStartedByUserId()),
                getValueOrEmpty(item.getFinishedAt()),
                getValueOrEmpty(item.getFinishedByUserId()),
                getValueOrEmpty(item.getStatus()), 
                getValueOrEmpty(item.getVersion()),
                getValueOrEmpty(item.getFileName()),
                getValueOrEmpty(item.getDuration()),
                getValueOrEmpty(item.getParticipants()),
                getValueOrEmpty(item.getUsers()),
                getValueOrEmpty(item.getShareBookings()),
                getValueOrEmpty(item.getCaseId()),
                getValueOrEmpty(item.getCourtId()),
                getValueOrEmpty(item.getBookingId()),
                getValueOrEmpty(item.getCaptureSessionId()),
                getValueOrEmpty(item.getRecordingId()),
                getValueOrEmpty(item.getParentRecordingId()),
                getValueOrEmpty(item.getParticipantIds()),
                getValueOrEmpty(item.getShareBookingIds()),
                getValueOrEmpty(item.getUserIds())
                )
            );
        }
        return rows;
    }

    private List<String> getFailedItemsHeaders() {
        return List.of("Reason for Failure", "Filename", "Display Name");
    }

    public List<List<String>> buildFailedItemsRows(List<FailedItem> items) {
        List<List<String>> rows = new ArrayList<>();

        for (FailedItem item : failedItems) {
            CSVArchiveListData archiveItem = item.getArchiveItem();
            
            rows.add(List.of(
                getValueOrEmpty(item.getReason()), 
                getValueOrEmpty(archiveItem.getFileName()),
                getValueOrEmpty(archiveItem.getArchiveName())   
                )
            );
        }
        return rows;
    }

    private String getValueOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }

}
