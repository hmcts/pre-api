package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.PassItem;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class MigrationTrackerService {

    private List<PassItem> migratedItems = new ArrayList<>();
    private List<FailedItem> failedItems = new ArrayList<>();
    private final CsvWriterService csvWriterService;

    public MigrationTrackerService(CsvWriterService csvWriterService) {
        this.csvWriterService = csvWriterService;
    }

    public void addMigratedItem(PassItem item) {
        migratedItems.add(item);
    }

    public void addFailedItem(FailedItem item) {
        failedItems.add(item);
    }

    public void writeMigratedItemsToCsv(String fileName) {
        List<String> headers = List.of(
            "regexPattern",
            "archiveName", 
            "caseReference",
            "isTest", 
            "scheduledFor",  
            "origin",
            "ingestAddress",
            "liveOutputURL",
            "startedAt",
            "startedByUserId",
            "finishedAt",
            "finishedByUserId",
            "status",
            "version",
            "fileName",
            "duration",

            "participants",
            "users",
            "shareBookings",

            "caseId", 
            "courtId", 
            "bookingId", 
            "captureSessionId",
            "recordingId",
            "parentRecordingId",

            "participantIds",
            "shareBookingIds",
            "userIds"
        );
        List<List<String>> rows = new ArrayList<>();

        for (PassItem item : migratedItems) {

            List<String> row = List.of(
                item.getRegexPattern(), 
                item.getArchiveName(),
                item.getCaseReference() != null ? item.getCaseReference() : "", 
                item.getIsTest() != null ? item.getIsTest().toString() : "", 
                item.getScheduledFor() != null ? item.getScheduledFor().toString() : "",
                item.getOrigin() != null ? item.getOrigin() : "", 
                item.getIngestAddress() != null ? item.getIngestAddress() : "",
                item.getLiveOutputURL() != null ? item.getLiveOutputURL() : "",
                item.getStartedAt() != null ? item.getStartedAt().toString() : "",
                item.getStartedByUserId() != null ? item.getStartedByUserId().toString() : "",
                item.getFinishedAt() != null ? item.getFinishedAt().toString() : "",
                item.getFinishedByUserId() != null ? item.getFinishedByUserId().toString() : "",
                item.getStatus() != null ? item.getStatus() : "", 
                item.getVersion() != null ? item.getVersion().toString() : "",
                item.getFileName() != null ? item.getFileName() : "",
                item.getDuration() != null ? item.getDuration().toString() : "",

                item.getParticipants() != null ? item.getParticipants().toString() : "",
                item.getUsers() != null ? item.getUsers().toString() : "",
                item.getShareBookings() != null ? item.getShareBookings().toString() : "",

                item.getCaseId() != null ? item.getCaseId().toString() : "",
                item.getCourtId() != null ? item.getCourtId().toString() : "",
                item.getBookingId() != null ? item.getBookingId().toString() : "",
                item.getCaptureSessionId() != null ? item.getCaptureSessionId().toString() : "",
                item.getRecordingId() != null ? item.getRecordingId().toString() : "",
                item.getParentRecordingId() != null ? item.getParentRecordingId().toString() : "",

                item.getParticipantIds() != null ? item.getParticipantIds().toString() : "",
                item.getShareBookingIds() != null ? item.getShareBookingIds().toString() : "",
                item.getUserIds() != null ? item.getUserIds().toString() : ""
                
                );
            rows.add(row);
        }

        csvWriterService.writeToCsv(headers, rows, fileName);
    }

    public void writeFailedItemsToCsv(String fileName) {
        List<String> headers = List.of(
            "Reason for Failure",
            "archiveName",
            "description",
            "createTime",
            "duration",
            "owner",
            "videoType",
            "audioType",
            "contentType",
            "farEndAddress"
        );
        
        List<List<String>> rows = new ArrayList<>();

        for (FailedItem item : failedItems) {
            CSVArchiveListData archiveItem = item.getArchiveItem();
            
            List<String> row = List.of(
                item.getReason(), 
                archiveItem.getArchiveName(),  
                archiveItem.getDescription(),  
                archiveItem.getCreateTime(),  
                String.valueOf(archiveItem.getDuration()),  
                archiveItem.getOwner(),  
                archiveItem.getVideoType(),  
                archiveItem.getAudioType(),  
                archiveItem.getContentType(),  
                archiveItem.getFarEndAddress()  
            );
            rows.add(row);
        }

        csvWriterService.writeToCsv(headers, rows, fileName);
    }

    public void writeAllToCsv() {
        writeMigratedItemsToCsv("migrated");
        writeFailedItemsToCsv("failed");
        Logger.getAnonymousLogger().info("Total Migrated Items: " + migratedItems.size());
        Logger.getAnonymousLogger().info("Total Failed Items: " + failedItems.size());

    }
}
