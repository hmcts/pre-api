package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;

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
    private final CsvWriterService csvWriterService;

    public MigrationTrackerService(CsvWriterService csvWriterService) {
        this.csvWriterService = csvWriterService;
    }
    
    /**
     * Adds a successfully migrated item to the list of migrated items.
     * @param item The PassItem representing a successfully migrated item.
     */
    public void addMigratedItem(PassItem item) {
        migratedItems.add(item);
    }

    /**
     * Adds a failed item to the list of failed items.
     * @param item The FailedItem representing an item that failed migration.
     */
    public void addFailedItem(FailedItem item) {
        failedItems.add(item);
    }

    /**
     * Converts an object to its string representation. If the object is null,
     * an empty string is returned instead. 
     * @param value The object to convert to a string. Can be null.
     * @return The string representation of the object, or an empty string if the object is null.
     */
    private String getValueOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Writes all successfully migrated items to a CSV file.
     * @param fileName  The name of the CSV file to be created.
     * @param outputDir The directory where the CSV file will be saved.
     */
    public void writeMigratedItemsToCsv(String fileName, String outputDir) {
        List<String> headers = List.of(
            "regexPattern", "archiveName", "caseReference", "isTest", "scheduledFor", "origin",
            "ingestAddress", "liveOutputURL", "startedAt", "startedByUserId", "finishedAt",
            "finishedByUserId", "status", "version", "fileName", "duration", "participants",
            "users", "shareBookings", "caseId", "courtId", "bookingId", "captureSessionId",
            "recordingId", "parentRecordingId", "participantIds", "shareBookingIds", "userIds"
        );

        List<List<String>> rows = new ArrayList<>();

        for (PassItem item : migratedItems) {

            List<String> row = List.of(
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
                );
            
            rows.add(row);
        }

        csvWriterService.writeToCsv(headers, rows, fileName, outputDir);
    }

    /**
     * Writes all failed items to a CSV file.
     * @param fileName  The name of the CSV file to be created.
     * @param outputDir The directory where the CSV file will be saved.
     */
    public void writeFailedItemsToCsv(String fileName, String outputDir) {
        List<String> headers = List.of(
            "Reason for Failure","archiveName","description","createTime",
            "duration","owner","videoType","audioType","contentType",
            "farEndAddress"
        );
        
        List<List<String>> rows = new ArrayList<>();

        for (FailedItem item : failedItems) {
            CSVArchiveListData archiveItem = item.getArchiveItem();
            
            List<String> row = List.of(
                getValueOrEmpty(item.getReason()), 
                getValueOrEmpty(archiveItem.getArchiveName()),   
                getValueOrEmpty(archiveItem.getDescription()),  
                getValueOrEmpty(archiveItem.getCreateTime()),   
                getValueOrEmpty(archiveItem.getDuration()),   
                getValueOrEmpty(archiveItem.getOwner()),  
                getValueOrEmpty(archiveItem.getVideoType()),  
                getValueOrEmpty(archiveItem.getAudioType()),   
                getValueOrEmpty(archiveItem.getContentType()),   
                getValueOrEmpty(archiveItem.getFarEndAddress()) 
            );
            rows.add(row);
        }

        csvWriterService.writeToCsv(headers, rows, fileName, outputDir);
    }

    /**
     * Writes both migrated and failed items to CSV files and logs the total counts,
     * and generates all reports at once.
     */
    public void writeAllToCsv() {
        writeMigratedItemsToCsv("Migrated","ZZZMigration Reports");
        writeFailedItemsToCsv("Failed","ZZZMigration Reports");
        Logger.getAnonymousLogger().info("Total Migrated Items: " + migratedItems.size());
        Logger.getAnonymousLogger().info("Total Failed Items: " + failedItems.size());

    }
}
