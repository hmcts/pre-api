package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.Optional;

@Service
public class DataValidationService {
    private final MigrationRecordService migrationRecordService;
    private final LoggingService loggingService;

    @Autowired
    public DataValidationService(final MigrationRecordService migrationRecordService,
                                 final LoggingService loggingService) {
        this.migrationRecordService = migrationRecordService;
        this.loggingService = loggingService;
    }

    /**
     * Validates the cleansed data against a series of checks.
     * @param cleansedData The cleansed data to validate.
     * @return A ServiceResult containing either the validated data or an error message.
     */
    public ServiceResult<ProcessedRecording> validateProcessedRecording(
            ProcessedRecording cleansedData) {

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
        }

        if (caseReference.length() > 24) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG,
                Constants.Reports.FILE_MISSING_DATA
            );
        }

        if ("COPY".equalsIgnoreCase(cleansedData.getExtractedRecordingVersion())) {
            Optional<MigrationRecord> currentRecord = migrationRecordService.findByArchiveId(
                cleansedData.getArchiveId());

            if (currentRecord.isPresent() && !isParentMigrated(currentRecord.get())) {
                return ServiceResultUtil.failure(
                    Constants.ErrorMessages.NO_PARENT_FOUND,
                    Constants.Reports.FILE_MISSING_DATA
                );
            }

        }

        if (!cleansedData.isPreferred()) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.NOT_PREFERRED,
                Constants.Reports.FILE_NOT_PREFERRED
            );
        }

        return ServiceResultUtil.success(cleansedData);
    }

    public ServiceResult<ProcessedRecording> validateResolvedRecording(
        ProcessedRecording cleansedData, String archiveName) {

        loggingService.logDebug("Processed Recording", cleansedData);
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

        return ServiceResultUtil.success(cleansedData);
    }

    private boolean isParentMigrated(MigrationRecord copy) {
        return migrationRecordService.getOrigFromCopy(copy)
            .map(MigrationRecord::getRecordingId)
            .isPresent();
    }
}
