package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.TransformationResult;

@Service    
public class DataValidationService {
    
    private static final String ERROR_FILE_EXTENSION = "File not .mp4 file.";
    private static final String ERROR_TIMESTAMP = "Invalid timestamp: Timestamp is null.";
    private static final String ERROR_COURT = "No valid court associated.";
    private static final String ERROR_MOST_RECENT_VERSION = "Recording is not the most recent version.";
    private static final String ERROR_CASE_REFERENCE = "No valid case reference.";

    @Autowired
    public DataValidationService() {
    }

    // =========================
    // Main Validation Logic
    // =========================

    /**
     * Validates the cleansed data against a series of checks.
     * @param cleansedData The cleansed data to validate.
     * @param archiveItem The original archive item for reference.
     * @return A map containing the validation result, including the cleansed data and any error messages.
     */
    public TransformationResult validateCleansedData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        
        // 1. File extension check
        if (!isFileExtensionValid(cleansedData)) {
            return failure(ERROR_FILE_EXTENSION);
        }

        // 2. Timestamp check
        if (cleansedData.getRecordingTimestamp() == null) {
            return failure(ERROR_TIMESTAMP);
        }

        // 3. Test data check
        if (cleansedData.isTest()) {
            return failure(cleansedData.getTestCheckResult().getReason());
        }

        // 4. Court check
        if (cleansedData.getCourt() == null) {
            return failure(ERROR_COURT);
        }

        // 5. Most recent version check
        if (!cleansedData.isMostRecentVersion()) {
            return failure(ERROR_MOST_RECENT_VERSION);
        }

        // 6. Case reference check
        if (cleansedData.getUrn() == null || cleansedData.getUrn().isEmpty()) {
            return failure(ERROR_CASE_REFERENCE);
        }

        return success(cleansedData);
    }

    // =========================
    // Specific Validation Methods
    // =========================

    private boolean isFileExtensionValid(CleansedData cleansedData) {
        String fileExtension = cleansedData.getFileExtension();
        if (fileExtension == null || fileExtension.isBlank()) {
            return false;
        }

        String lowerExtension = fileExtension.toLowerCase();

        return !(lowerExtension.equals(".raw") || lowerExtension.equals(".ra"));
    }

    // =========================
    // Helper Methods
    // =========================

    private TransformationResult failure(String errorMessage) {
        return new TransformationResult(null, errorMessage);
    }

    private TransformationResult success(CleansedData cleansedData) {
        return new TransformationResult(cleansedData, null);
    }
}
