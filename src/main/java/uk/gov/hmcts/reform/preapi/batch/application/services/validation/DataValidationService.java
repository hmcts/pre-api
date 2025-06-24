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

        if ("COPY".equalsIgnoreCase(cleansedData.getExtractedRecordingVersion()) 
            && !cleansedData.isMostRecentVersion()) {
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

        if (cleansedData.getRecordingVersionNumber() > 1) {

            String recordingCacheKey = cacheService.generateEntityCacheKey(
                "recording",
                cleansedData.getCaseReference(),
                cleansedData.getDefendantLastName(),
                cleansedData.getWitnessFirstName(),
                cleansedData.getOrigVersionNumberStr()
            );

            String parentArchiveKey = "archiveName:orig:" + cleansedData.getOrigVersionNumberStr();
            String parentOrig = cacheService.getHashValue(recordingCacheKey, parentArchiveKey, String.class);

            if (parentOrig == null) {
                return ServiceResultUtil.failure(
                    Constants.ErrorMessages.NO_PARENT_FOUND,
                    Constants.Reports.FILE_MISSING_DATA
                );
            }
        }

        return ServiceResultUtil.success(cleansedData);
    }


    public ServiceResult<ProcessedRecording> validateExemptionRecording(
        ProcessedRecording cleansedData, String archiveName) {

        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_COURT, 
                Constants.Reports.FILE_MISSING_DATA);
        }

        String caseReference = cleansedData.getCaseReference();
        if (caseReference == null || caseReference.length() < 9) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT, 
                Constants.Reports.FILE_MISSING_DATA);
        }
        if (caseReference.length() > 24) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG, 
                Constants.Reports.FILE_MISSING_DATA);
        }

        String witness = cleansedData.getWitnessFirstName();
        if (witness == null || witness.trim().isEmpty()) {
            return ServiceResultUtil.failure("Missing or empty witness first name", 
                Constants.Reports.FILE_MISSING_DATA);
        }

        String defendant = cleansedData.getDefendantLastName();
        if (defendant == null || defendant.trim().isEmpty()) {
            return ServiceResultUtil.failure("Missing or empty defendant last name", 
                Constants.Reports.FILE_MISSING_DATA);
        }

        if (cleansedData.getRecordingVersionNumber() < 1) {
            return ServiceResultUtil.failure("Invalid recording version number", 
                Constants.Reports.FILE_MISSING_DATA);
        }

        if (cleansedData.getFileName() == null || cleansedData.getFileName().isBlank()) {
            return ServiceResultUtil.failure("Missing file name", Constants.Reports.FILE_MISSING_DATA);
        }

        if (cleansedData.getFileExtension() == null || cleansedData.getFileExtension().trim().isEmpty()) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.INVALID_FILE_EXTENSION, 
                Constants.Reports.FILE_INVALID_FORMAT);
        }

        return ServiceResultUtil.success(cleansedData);
    }
}
