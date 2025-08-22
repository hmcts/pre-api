package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.entities.Court;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = DataValidationService.class)
public class DataValidationServiceTest {
    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private MigrationRecordRepository migrationRecordRepository;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private DataValidationService dataValidationService;

    @Test
    @DisplayName("Should return failure when court is null")
    void validateProcessedRecordingCourtNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.MISSING_COURT);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when is not most recent version")
    void validateProcessedRecordingNotIsMostRecentVersion() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .caseReference("123456789")
            .extractedRecordingVersion("COPY")
            .isMostRecentVersion(false)
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.NOT_MOST_RECENT_VERSION);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.NOT_MOST_RECENT.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is null")
    void validateProcessedRecordingCaseRefNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is too short")
    void validateProcessedRecordingCaseRefTooShort() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("SHORT")
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is too long")
    void validateProcessedRecordingCaseRefTooLong() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("_25_CHAR_STR_25_CHAR_STR_")
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when no parent recording found")
    void validateProcessedRecordingVersionGT1NoExistingMetadata() {
        MigrationRecord currentRecord = new MigrationRecord();
        
        when(migrationRecordService.findByArchiveId("ARCHIVE123")).thenReturn(Optional.of(currentRecord));

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .isPreferred(true)
            .caseReference("case_reference")
            .witnessFirstName("witness")
            .defendantLastName("defendant")
            .recordingVersionNumber(2)
            .extractedRecordingVersion("COPY")
            .archiveId("ARCHIVE123")
            .build();


        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.NO_PARENT_FOUND);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should succeed for COPY in processed when parent exists and most recent")
    void validateProcessedRecordingCopySuccessWhenParentAndMostRecent() {
        MigrationRecord current = new MigrationRecord();

        when(migrationRecordService.findByArchiveId("ARCH123")).thenReturn(Optional.of(current));
        when(migrationRecordRepository.getIsMostRecent("ARCH123")).thenReturn(Optional.of(true));

        ProcessedRecording pr = ProcessedRecording.builder()
            .court(new Court())
            .archiveId("ARCH123")
            .extractedRecordingVersion("COPY")
            .caseReference("123456789")
            .isPreferred(true)
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(pr);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(pr);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should return failure when recording is not preferred")
    void validateProcessedRecordingNonPreferred() {

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .archiveId("ARCH123")
            .isMostRecentVersion(true)
            .caseReference("123456789")
            .extractedRecordingVersion("COPY")
            .isPreferred(false)
            .build();

        when(migrationRecordService.findByArchiveId("ARCH123"))
            .thenReturn(Optional.empty());

        when(migrationRecordRepository.getIsMostRecent("ARCH123"))
            .thenReturn(Optional.of(true));

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.NOT_PREFERRED);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.ALTERNATIVE_AVAILABLE.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return success when all fields are valid")
    void validateProcessedRecordingSuccessAllValidFields() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("12345678901")
            .extractedRecordingVersion("ORIGINAL")
            .isPreferred(true)
            .build();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isEqualTo(processedRecording);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCategory()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should return failure when court is null")
    void validateResolvedRecordingCourtIsNull() {
        ProcessedRecording data =  new ProcessedRecording();
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.MISSING_COURT);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is null")
    void validateResolvedRecordingCaseReferenceIsNull() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is too short")
    void validateResolvedRecordingCaseReferenceTooShort() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("1");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is too long")
    void validateResolvedRecordingCaseReferenceTooLong() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789012345678901234"); // 25 chars
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when witness is null")
    void validateResolvedRecordingWitnessNull() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Missing or empty witness first name");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when witness is empty")
    void validateResolvedRecordingWitnessEmpty() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Missing or empty witness first name");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when defendant is null")
    void validateResolvedRecordingDefendantNull() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("witness");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Missing or empty defendant last name");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when defendant is empty")
    void validateResolvedRecordingDefendantEmpty() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("witness");
        data.setDefendantLastName("");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Missing or empty defendant last name");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when version number less than 1")
    void validateResolvedRecordingVersionLT1() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("witness");
        data.setDefendantLastName("defendant");
        data.setRecordingVersionNumber(0);
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Invalid recording version number");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when filename is null")
    void validateResolvedRecordingFilenameIsNull() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("witness");
        data.setDefendantLastName("defendant");
        data.setRecordingVersionNumber(1);
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Missing file name");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when filename is empty")
    void validateResolvedRecordingFilenameIsEmpty() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("witness");
        data.setDefendantLastName("defendant");
        data.setRecordingVersionNumber(1);
        data.setFileName("");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Missing file name");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.INCOMPLETE_DATA.toString());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return success when validating resolved recording")
    void validateResolvedRecordingSuccess() {
        ProcessedRecording data =  new ProcessedRecording();
        data.setCourt(new Court());
        data.setCaseReference("0123456789");
        data.setWitnessFirstName("witness");
        data.setDefendantLastName("defendant");
        data.setRecordingVersionNumber(1);
        data.setFileName("test.mp4");
        String archiveName = "ARCHIVE123";

        ServiceResult<ProcessedRecording> result = dataValidationService.validateResolvedRecording(data, archiveName);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isEqualTo(data);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCategory()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }
}
