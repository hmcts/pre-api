package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

@Service    
public class DataValidationService {    

    /**
     * Validates the cleansed data against a series of checks.
     * @param cleansedData The cleansed data to validate.
     * @param archiveItem The original archive item for reference.
     * @return A ServiceResult containing either the validated data or an error message.
     */
    public ServiceResult<CleansedData> validateCleansedData(
            CleansedData cleansedData, CSVArchiveListData archiveItem) {        
        if (!isFileExtensionValid(cleansedData)) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.INVALID_FILE_EXTENSION, "Invalid File Format");
        }

        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_COURT, "Missing data");
        }

        if (!cleansedData.isMostRecentVersion()) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.NOT_MOST_RECENT_VERSION, "Not most recent");
        }

        if (cleansedData.getCaseReference() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_CASE_REFERENCE, "Missing data");
        }

        if (cleansedData.getCaseReference().length() > 24) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG, "Missing data");
        }

        return ServiceResultUtil.success(cleansedData);
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
}
