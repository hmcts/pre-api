package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;

import java.util.logging.Logger;


@Service    
public class DataValidationService {

    private final MigrationTrackerService migrationTrackerService;
    
    @Autowired
    public DataValidationService(MigrationTrackerService migrationTrackerService) {
        this.migrationTrackerService = migrationTrackerService;
    }

    public boolean validateCleansedData(
        CleansedData cleansedData, 
        CSVArchiveListData archiveItem) {


        if (!validateDate(cleansedData, archiveItem)) {
            Logger.getAnonymousLogger().info("failed validate date");
            return false;
        }

        if (!validateTestData(cleansedData, archiveItem)) {
            Logger.getAnonymousLogger().info("failed validate test");
            return false;
        }

        if (!validateCourt(cleansedData, archiveItem)) {
            Logger.getAnonymousLogger().info("failed validate court");

            return false;
        }

        return validateCaseReference(cleansedData, archiveItem);
    }

    private boolean validateDate(CleansedData cleansedData, CSVArchiveListData archiveItem) {

        if (cleansedData.getRecordingTimestamp() == null) {
            handleFailure(archiveItem, "Invalid timestamp: Timestamp is null.");
            return false;
        }
        return true;
    }

    private boolean validateTestData(
        CleansedData cleansedData, 
        CSVArchiveListData archiveItem) {
        if (cleansedData.isTest()) {
            handleFailure(archiveItem, cleansedData.getTestCheckResult().getReason());
            return false;
        }
        return true;
    }

    private boolean validateCourt(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (cleansedData.getCourt() == null) {
            handleFailure(archiveItem, "No valid court associated.");
            return false;
        }
        return true;
    }

    private boolean validateCaseReference(
        CleansedData cleansedData, 
        CSVArchiveListData archiveItem) {
        if (cleansedData.getUrn() == null || cleansedData.getUrn().isEmpty()) {
            handleFailure(archiveItem, "No valid case reference (URN).");
            return false;
        }
        return true;
    }

    private void handleFailure(CSVArchiveListData archiveItem, String reason) {
        migrationTrackerService.addFailedItem(new FailedItem(archiveItem, reason));
    }

   
}
