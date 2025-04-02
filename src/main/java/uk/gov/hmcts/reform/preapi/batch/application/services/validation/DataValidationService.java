package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

@Service
public class DataValidationService {
    private final InMemoryCacheService cacheService;

    @Autowired
    public DataValidationService(InMemoryCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Validates the cleansed data against a series of checks.
     * @param cleansedData The cleansed data to validate.
     * @param archiveItem The original archive item for reference.
     * @return A ServiceResult containing either the validated data or an error message.
     */
    public ServiceResult<ProcessedRecording> validateProcessedRecording(
            ProcessedRecording cleansedData, CSVArchiveListData archiveItem) {

        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_COURT,
                Constants.Reports.FILE_MISSING_DATA);
        }

        if (!cleansedData.isMostRecentVersion()) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.NOT_MOST_RECENT_VERSION,
                Constants.Reports.FILE_NOT_RECENT);
        }

        String caseReference = cleansedData.getCaseReference();
        if (caseReference == null || caseReference.length() < 9) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT,
                Constants.Reports.FILE_MISSING_DATA
            );
        }  else if (caseReference.length() > 24) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG,
                Constants.Reports.FILE_MISSING_DATA
            );
        }

        String participantPair = cleansedData.getWitnessFirstName() + '-' + cleansedData.getDefendantLastName();
        String baseKey = cacheService.generateBaseKey(cleansedData.getCaseReference(), participantPair);

        if (cleansedData.getRecordingVersionNumber() > 1) {
            String existingMetadata = cacheService.getHashValue(baseKey, "recordingMetadata", String.class);
            if (existingMetadata == null) {
                return ServiceResultUtil.failure(
                    Constants.ErrorMessages.NO_PARENT_FOUND,
                    Constants.Reports.FILE_MISSING_DATA
                );
            }
        }

        return ServiceResultUtil.success(cleansedData);
    }
}
