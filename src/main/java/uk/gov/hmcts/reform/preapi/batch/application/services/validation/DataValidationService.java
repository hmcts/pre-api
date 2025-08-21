package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.Optional;

@Service
public class DataValidationService {
    private final MigrationRecordService migrationRecordService;
    private final MigrationRecordRepository migrationRecordRepository;
    private final LoggingService loggingService;

    @Autowired
    public DataValidationService(final MigrationRecordService migrationRecordService,
                                 final MigrationRecordRepository migrationRecordRepository,
                                 final LoggingService loggingService) {
        this.migrationRecordService = migrationRecordService;
        this.migrationRecordRepository = migrationRecordRepository;
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
                VfFailureReason.INCOMPLETE_DATA.toString());
        }

        if ("COPY".equalsIgnoreCase(cleansedData.getExtractedRecordingVersion())) {
            Optional<MigrationRecord> currentRecord = migrationRecordService.findByArchiveId(
                cleansedData.getArchiveId());

            if (currentRecord.isPresent() && currentRecord.get().getParentTempId() == null) {
                return ServiceResultUtil.failure(
                    Constants.ErrorMessages.NO_PARENT_FOUND,
                    VfFailureReason.INCOMPLETE_DATA.toString()
                );
            }
        }

        if ("COPY".equalsIgnoreCase(cleansedData.getExtractedRecordingVersion())) {
            boolean isMostRecent = Boolean.TRUE.equals(
                migrationRecordRepository.getIsMostRecent(cleansedData.getArchiveId())
            );
            
            if (!isMostRecent) {
                return ServiceResultUtil.failure(
                    Constants.ErrorMessages.NOT_MOST_RECENT_VERSION,
                    VfFailureReason.NOT_MOST_RECENT.toString()
                );
            }
        }

        

        String caseReference = cleansedData.getCaseReference();
        if (caseReference == null || caseReference.length() < 7) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT,
                VfFailureReason.INCOMPLETE_DATA.toString()
            );
        }

        if (caseReference.length() > 24) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG,
                VfFailureReason.INCOMPLETE_DATA.toString()
            );
        }

        if (!cleansedData.isPreferred()) {
            return ServiceResultUtil.failure(
                Constants.ErrorMessages.NOT_PREFERRED,
                VfFailureReason.ALTERNATIVE_AVAILABLE.toString()
            );
        }

       

        return ServiceResultUtil.success(cleansedData);
    }

    public ServiceResult<ProcessedRecording> validateResolvedRecording(
        ProcessedRecording cleansedData, String archiveName) {

        loggingService.logDebug("Processed Recording", cleansedData);
        if (cleansedData.getCourt() == null) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.MISSING_COURT,
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }


        if ("COPY".equalsIgnoreCase(cleansedData.getExtractedRecordingVersion())) {
            boolean isMostRecent = migrationRecordRepository
                .findByArchiveId(cleansedData.getArchiveId())
                .map(mr -> Boolean.TRUE.equals(mr.getIsMostRecent()))
                .orElse(false);

            if (!isMostRecent) {
                return ServiceResultUtil.failure(
                    Constants.ErrorMessages.NOT_MOST_RECENT_VERSION,
                    VfFailureReason.NOT_MOST_RECENT.toString()
                );
            }
        }

        String caseReference = cleansedData.getCaseReference();
        if (caseReference == null || caseReference.length() < 9) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT,
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }

        if (caseReference.length() > 24) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG,
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }

        String witness = cleansedData.getWitnessFirstName();
        if (witness == null || witness.trim().isEmpty()) {
            return ServiceResultUtil.failure("Missing or empty witness first name",
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }

        String defendant = cleansedData.getDefendantLastName();
        if (defendant == null || defendant.trim().isEmpty()) {
            return ServiceResultUtil.failure("Missing or empty defendant last name",
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }

        if (cleansedData.getRecordingVersionNumber() < 1) {
            return ServiceResultUtil.failure("Invalid recording version number",
                                             VfFailureReason.INCOMPLETE_DATA.toString());
        }

        if (cleansedData.getFileName() == null || cleansedData.getFileName().isBlank()) {
            return ServiceResultUtil.failure("Missing file name", VfFailureReason.INCOMPLETE_DATA.toString());
        }

        return ServiceResultUtil.success(cleansedData);
    }

}
