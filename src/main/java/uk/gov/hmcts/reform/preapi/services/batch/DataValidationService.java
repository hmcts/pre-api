package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.ServiceResult;
import uk.gov.hmcts.reform.preapi.entities.batch.TestItem;
import uk.gov.hmcts.reform.preapi.util.batch.ServiceResultUtil;

import java.util.Arrays;
import java.util.List;

@Service    
public class DataValidationService {
    public static final List<String> TEST_KEYWORDS = Arrays.asList("test", "demo", "unknown", "training", "T35T");
    public static final int MIN_RECORDING_DURATION = 10;
    private static final String ERROR_FILE_EXTENSION = "File not .mp4 file.";
    private static final String ERROR_TIMESTAMP = "Invalid timestamp: Timestamp is null.";
    private static final String ERROR_COURT = "No valid court associated.";
    private static final String ERROR_MOST_RECENT_VERSION = "Recording is not the most recent version.";
    private static final String ERROR_CASE_REFERENCE = "No valid case reference.";
    private static final String ERROR_CASE_REFERENCE_LONG = "Case reference longer than 24 chars.";


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
    public ServiceResult<CleansedData> validateCleansedData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        TestItem testCheck = checkIsTest(archiveItem);
        if (testCheck.isTest()) {
            return ServiceResultUtil.createFailureReponse(testCheck.getReason());
        }

        if (!isFileExtensionValid(cleansedData)) {
            return ServiceResultUtil.createFailureReponse(ERROR_FILE_EXTENSION);
        }

        if (cleansedData.getRecordingTimestamp() == null) {
            return ServiceResultUtil.createFailureReponse(ERROR_TIMESTAMP);
        }

        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.createFailureReponse(ERROR_COURT);
        }

        if (!cleansedData.isMostRecentVersion()) {
            return ServiceResultUtil.createFailureReponse(ERROR_MOST_RECENT_VERSION);
        }

        if (cleansedData.getCaseReference() == null) {
            return ServiceResultUtil.createFailureReponse(ERROR_CASE_REFERENCE);
        }

        if (cleansedData.getCaseReference().length() > 24){
            return ServiceResultUtil.createFailureReponse(ERROR_CASE_REFERENCE_LONG);
        }

        return ServiceResultUtil.createSuccessResponse(cleansedData);
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

        return !(lowerExtension.equals(".raw") || lowerExtension.equals(".ra")
                || lowerExtension.equals("ra") || lowerExtension.equals("raw"));
    }

    public TestItem checkIsTest(CSVArchiveListData archiveItem) {
        if (archiveItem == null || archiveItem.getArchiveName() == null) {
            throw new IllegalArgumentException("Archive item or name cannot be null.");
        }

        String lowerName = archiveItem.getArchiveName().toLowerCase();
        StringBuilder reasons = new StringBuilder();

        for (String keyword : TEST_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                reasons.append("Archive name contains '").append(keyword).append("'. ");
            }
        }

        if (archiveItem.getDuration() < MIN_RECORDING_DURATION) {
            reasons.append("Duration is less than 10 seconds. ");
        }

        return reasons.length() > 0 
            ? new TestItem(true, reasons.toString().trim())
            : new TestItem(false, "No test related criteria met.");
    }

}
