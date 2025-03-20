package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

@Service
public class DataValidationService {

    private LoggingService loggingService;

    public DataValidationService(
        LoggingService loggingService
    ) {
        this.loggingService = loggingService;
    }

    /**
     * Validates the cleansed data against a series of checks.
     * @param cleansedData The cleansed data to validate.
     * @param archiveItem The original archive item for reference.
     * @return A ServiceResult containing either the validated data or an error message.
     */
    public ServiceResult<CleansedData> validateCleansedData(
            CleansedData cleansedData, CSVArchiveListData archiveItem) {

        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_COURT,
                Constants.Reports.FILE_MISSING_DATA);
        }

        if (!cleansedData.isMostRecentVersion()) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.NOT_MOST_RECENT_VERSION,
                Constants.Reports.FILE_NOT_RECENT);
        }

        if (cleansedData.getCaseReference() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_CASE_REFERENCE,
                Constants.Reports.FILE_MISSING_DATA);
        }

        if (cleansedData.getCaseReference().length() > 24) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG,
                Constants.Reports.FILE_MISSING_DATA);
        }

        return ServiceResultUtil.success(cleansedData);
    }
}
