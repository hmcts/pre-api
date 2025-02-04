package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.TransformationResult;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;

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
        TransformationResult validationResult;

        validationResult = validateFileExtension(cleansedData, archiveItem);
        if (validationResult.getErrorMessage() != null) {
            return validationResult; 
        }

        validationResult = validateDate(cleansedData, archiveItem);
        if (validationResult.getErrorMessage() != null) {
            return validationResult;
        }

        validationResult = validateTestData(cleansedData, archiveItem);
        if (validationResult.getErrorMessage() != null) {
            return validationResult; 
        }

        validationResult = validateCourt(cleansedData, archiveItem);
        if (validationResult.getErrorMessage() != null) {
            return validationResult; 
        }

        validationResult = validateMostRecentVersion(cleansedData, archiveItem);
        if (validationResult.getErrorMessage() != null) {
            return validationResult; 
        }

        return validateCaseReference(cleansedData, archiveItem);
    }

    // =========================
    // Specific Validation Methods
    // =========================

    private TransformationResult validateFileExtension(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        String fileExtension = cleansedData.getFileExtension();
        fileExtension = (fileExtension == null || fileExtension.isBlank()) ? "" : fileExtension.toLowerCase();

        if (fileExtension.isBlank() 
            || ".raw".equalsIgnoreCase(fileExtension) 
            || ".ra".equalsIgnoreCase(fileExtension)) {
            return createErrorResponse(ERROR_FILE_EXTENSION);
        }

        return createSuccessResponse(cleansedData);
    }

    private TransformationResult validateDate(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (cleansedData.getRecordingTimestamp() == null) {
            return createErrorResponse(ERROR_TIMESTAMP);
        }
        return createSuccessResponse(cleansedData);
    }


    private TransformationResult validateTestData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (cleansedData.isTest()) {
            return createErrorResponse(cleansedData.getTestCheckResult().getReason());
        }
        return createSuccessResponse(cleansedData);
    }

    private TransformationResult validateCourt(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (cleansedData.getCourt() == null) {
            return createErrorResponse(ERROR_COURT);
        }
        return createSuccessResponse(cleansedData);
    }

    private TransformationResult validateMostRecentVersion(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        if (!cleansedData.isMostRecentVersion()) {
            return createErrorResponse(ERROR_MOST_RECENT_VERSION);
        }
        return createSuccessResponse(cleansedData);
    }

    private TransformationResult validateCaseReference(
        CleansedData cleansedData, 
        CSVArchiveListData archiveItem) {
        if (cleansedData.getUrn() == null || cleansedData.getUrn().isEmpty()) {
            return createErrorResponse(ERROR_CASE_REFERENCE);
        }
        return createSuccessResponse(cleansedData);
    }

    // =========================
    // Helper Methods
    // =========================

    /**
     * Creates an error response with the specified error message.
     * @param errorMessage The error message to include in the response.
     * @return An object containing the error response.
     */
    private TransformationResult createErrorResponse(String errorMessage) {
        TransformationResult errorResponse = new TransformationResult(null, errorMessage);
        return errorResponse;
    }

    /**
     * Creates a success response with the cleansed data.
     * @param cleansedData The cleansed data to include in the response.
     * @return An object containing the success response.
     */
    private TransformationResult createSuccessResponse(CleansedData cleansedData) {
        TransformationResult successResponse = new TransformationResult(cleansedData, null);
        return successResponse;
    }
}
