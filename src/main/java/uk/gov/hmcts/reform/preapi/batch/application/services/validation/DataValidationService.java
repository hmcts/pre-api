package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.time.LocalDate;
import java.util.Set;

@Service    
public class DataValidationService {    
    private static final Set<String> TEST_KEYWORDS = Set.of(
        "test", "demo", "unknown", "training", "T35T",
        "sample", "mock", "dummy", "example", "playback", "predefined"
    );
    private static final int MIN_RECORDING_DURATION = 10;
    private static final LocalDate GO_LIVE_DATE = LocalDate.of(2019, 5, 23);
    
    private static final String ERROR_FILE_EXTENSION = 
        "Invalid file format: Only .mp4 files are allowed.";
    private static final String ERROR_TIMESTAMP = 
        "Invalid timestamp: The timestamp cannot be null.";
    private static final String ERROR_COURT = 
        "Validation failed: No valid court is associated with this recording.";
    private static final String ERROR_MOST_RECENT_VERSION = 
        "Validation failed: The recording is not the most recent version.";
    private static final String ERROR_CASE_REFERENCE = 
        "Validation failed: Missing or invalid case reference.";
    private static final String ERROR_CASE_REFERENCE_LONG = 
        "Validation failed: Case reference exceeds the 24-character limit.";
    private static final String ERROR_PREDATES_GO_LIVE = 
        "Validation failed: Recording date is before the go-live date (23/05/2019).";
    private static final String ERROR_INVALID_ARCHIVE = 
        "Validation failed: Archive item or archive name cannot be null.";

    /**
     * Validates the cleansed data against a series of checks.
     * @param cleansedData The cleansed data to validate.
     * @param archiveItem The original archive item for reference.
     * @return A ServiceResult containing either the validated data or an error message.
     */
    public ServiceResult<CleansedData> validateCleansedData(
            CleansedData cleansedData, CSVArchiveListData archiveItem) {        
        if (!isDateAfterGoLive(cleansedData)) {
            return ServiceResultUtil.failure(ERROR_PREDATES_GO_LIVE);
        }

        TestItem testCheck = checkIsTest(archiveItem);
        if (testCheck.isTest()) {
            return ServiceResultUtil.failure(testCheck.getReason());
        }

        if (!isFileExtensionValid(cleansedData)) {
            return ServiceResultUtil.failure(ERROR_FILE_EXTENSION);
        }

        if (cleansedData.getRecordingTimestamp() == null) {
            return ServiceResultUtil.failure(ERROR_TIMESTAMP);
        }

        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.failure(ERROR_COURT);
        }

        if (!cleansedData.isMostRecentVersion()) {
            return ServiceResultUtil.failure(ERROR_MOST_RECENT_VERSION);
        }

        if (cleansedData.getCaseReference() == null) {
            return ServiceResultUtil.failure(ERROR_CASE_REFERENCE);
        }

        if (cleansedData.getCaseReference().length() > 24) {
            return ServiceResultUtil.failure(ERROR_CASE_REFERENCE_LONG);
        }

        return ServiceResultUtil.success(cleansedData);
    }

    /**
     * Checks if the recording date is after the go-live date.
     * @param cleansedData The cleansed data containing the recording timestamp
     * @return true if the date is valid, false otherwise
     */
    private boolean isDateAfterGoLive(CleansedData cleansedData) {
        var recordingTimestamp = cleansedData.getRecordingTimestamp();
        if (recordingTimestamp == null) {
            return false;
        }

        LocalDate recordingDate = recordingTimestamp.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate();

        return !recordingDate.isBefore(GO_LIVE_DATE);
    }

    /**
     * Validates the file extension.
     * @param cleansedData The cleansed data containing the file extension
     * @return true if the extension is valid, false otherwise
     */
    private boolean isFileExtensionValid(CleansedData cleansedData) {
        if (cleansedData.getFileExtension() == null) {
            return false;
        }

        String lowerExtension = cleansedData.getFileExtension().toLowerCase();
        return !lowerExtension.equals(".raw") && !lowerExtension.equals(".ra")
               && !lowerExtension.equals("ra") && !lowerExtension.equals("raw");
    }

    /**
     * Checks if the archive item is a test item.
     * @param archiveItem The archive item to check
     * @return A TestItem containing the test status and reason
     * @throws IllegalArgumentException if the archive item or name is null
     */
    public TestItem checkIsTest(CSVArchiveListData archiveItem) {
        if (archiveItem == null || archiveItem.getArchiveName() == null) {
            throw new IllegalArgumentException(ERROR_INVALID_ARCHIVE);
        }

        String lowerName = archiveItem.getArchiveName().toLowerCase();

        for (String keyword : TEST_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                return new TestItem(true, "Archive name contains '" + keyword + "'.");
            }
        }

        if (archiveItem.getDuration() < MIN_RECORDING_DURATION) {
            return new TestItem(true, "Duration is less than " + MIN_RECORDING_DURATION + " seconds.");
        }

        return new TestItem(false, "No test related criteria met.");
    }
}
