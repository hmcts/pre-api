package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItemXML;
import uk.gov.hmcts.reform.preapi.entities.batch.UnifiedArchiveData;

import java.util.logging.Logger;

@Service    
public class DataValidationService {

    private final MigrationTrackerService migrationTrackerService;
    private final MigrationTrackerServiceXML migrationTrackerServiceXML;
    
    @Autowired
    public DataValidationService(
        MigrationTrackerService migrationTrackerService, 
        MigrationTrackerServiceXML migrationTrackerServiceXML
    ) {
        this.migrationTrackerService = migrationTrackerService;
        this.migrationTrackerServiceXML = migrationTrackerServiceXML;
    }

    public boolean validateCleansedData(
        CleansedData cleansedData, 
        CSVArchiveListData archiveItem) {

        if (!validateFileExtension(cleansedData, archiveItem)) {
            return false;
        }

        if (!validateDate(cleansedData, archiveItem)) {
            return false;
        }

        if (!validateTestData(cleansedData, archiveItem)) {
            return false;
        }

        if (!validateCourt(cleansedData, archiveItem)) {
            return false;
        }


        return validateCaseReference(cleansedData, archiveItem);
    }

    public boolean validateCleansedDataXML(
        CleansedData cleansedData, 
        UnifiedArchiveData archiveItem) {

        if (!validateFileExtensionXML(cleansedData, archiveItem)) {
            return false;
        }

        if (!validateDateXML(cleansedData, archiveItem)) {
            return false;
        }

        if (!validateTestDataXML(cleansedData, archiveItem)) {
            return false;
        }

        if (!validateCourtXML(cleansedData, archiveItem)) {
            return false;
        }


        return validateCaseReferenceXML(cleansedData, archiveItem);
    }

    private boolean validateFileExtension(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        String fileExtension = cleansedData.getFileExtension();
        fileExtension = (fileExtension == null || fileExtension.isBlank()) ? "" : fileExtension.toLowerCase();

        if (fileExtension.isBlank()) {
            handleFailure(archiveItem, "File not .mp4 file.");
            return false;
        }

        if (".raw".equalsIgnoreCase(fileExtension) || ".ra".equalsIgnoreCase(fileExtension)) {
            handleFailure(archiveItem, "File with .raw extension not to migrate");
            return false;
        }
        return true;
    }

    private boolean validateFileExtensionXML(CleansedData cleansedData, UnifiedArchiveData archiveItem) {
        String fileExtension = cleansedData.getFileExtension();
        fileExtension = (fileExtension == null || fileExtension.isBlank()) ? "" : fileExtension.toLowerCase();
        if ("From XML".equals(archiveItem.getAdditionalData())) {
            return true;
        }

        if (fileExtension.isBlank()) {
            handleFailureXML(archiveItem, "File not .mp4 file.");
            return false;
        }

        if (".raw".equalsIgnoreCase(fileExtension) || ".ra".equalsIgnoreCase(fileExtension)) {
            handleFailureXML(archiveItem, "File with .raw extension not to migrate");
            return false;
        }
        return true;
    }

    private boolean validateDate(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (cleansedData.getRecordingTimestamp() == null) {
            handleFailure(archiveItem, "Invalid timestamp: Timestamp is null.");
            return false;
        }
        return true;
    }

    private boolean validateDateXML(CleansedData cleansedData, UnifiedArchiveData archiveItem) {
        if (cleansedData.getRecordingTimestamp() == null) {
            handleFailureXML(archiveItem, "Invalid timestamp: Timestamp is null.");
            return false;
        }
        return true;
    }

    private boolean validateTestData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (cleansedData.isTest()) {
            handleFailure(archiveItem, cleansedData.getTestCheckResult().getReason());
            return false;
        }
        return true;
    }

    private boolean validateTestDataXML(CleansedData cleansedData, UnifiedArchiveData archiveItem) {
        if (cleansedData.isTest()) {
            handleFailureXML(archiveItem, cleansedData.getTestCheckResult().getReason());
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

    private boolean validateCourtXML(CleansedData cleansedData, UnifiedArchiveData archiveItem) {
        if (cleansedData.getCourt() == null) {
            Logger.getAnonymousLogger().info("Cleansed data is: " + cleansedData);
            handleFailureXML(archiveItem, "No valid court associated.");
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

    private boolean validateCaseReferenceXML(
        CleansedData cleansedData, 
        UnifiedArchiveData archiveItem) {
        if (cleansedData.getUrn() == null || cleansedData.getUrn().isEmpty()) {
            handleFailureXML(archiveItem, "No valid case reference (URN).");
            return false;
        }
        return true;
    }

    private void handleFailure(CSVArchiveListData archiveItem, String reason) {
        migrationTrackerService.addFailedItem(new FailedItem(archiveItem, reason));
    }

    private void handleFailureXML(UnifiedArchiveData archiveItem, String reason) {
        migrationTrackerServiceXML.addFailedItem(new FailedItemXML(archiveItem, reason));
    }
   
}
