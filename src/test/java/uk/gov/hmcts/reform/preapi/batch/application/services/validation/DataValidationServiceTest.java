package uk.gov.hmcts.reform.preapi.batch.application.services.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.entities.Court;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = DataValidationService.class)
public class DataValidationServiceTest {
    @MockitoBean
    private InMemoryCacheService inMemoryCacheService;

    @Autowired
    private DataValidationService dataValidationService;

    @Test
    @DisplayName("Should return failure when court is null")
    void validateProcessedRecordingCourtNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .build();
        CSVArchiveListData archive = new CSVArchiveListData();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.MISSING_COURT);
        assertThat(result.getCategory()).isEqualTo(Constants.Reports.FILE_MISSING_DATA);
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
        CSVArchiveListData archive = new CSVArchiveListData();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.NOT_MOST_RECENT_VERSION);
        assertThat(result.getCategory()).isEqualTo(Constants.Reports.FILE_NOT_RECENT);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is null")
    void validateProcessedRecordingCaseRefNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .build();
        CSVArchiveListData archive = new CSVArchiveListData();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT);
        assertThat(result.getCategory()).isEqualTo(Constants.Reports.FILE_MISSING_DATA);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when case reference is too short")
    void validateProcessedRecordingCaseRefTooShort() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("SHORTREF")
            .build();
        CSVArchiveListData archive = new CSVArchiveListData();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_SHORT);
        assertThat(result.getCategory()).isEqualTo(Constants.Reports.FILE_MISSING_DATA);
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
        CSVArchiveListData archive = new CSVArchiveListData();

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.CASE_REFERENCE_TOO_LONG);
        assertThat(result.getCategory()).isEqualTo(Constants.Reports.FILE_MISSING_DATA);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return failure when no parent recording found")
    void validateProcessedRecordingVersionGT1NoExistingMetadata() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("case_reference")
            .witnessFirstName("witness")
            .defendantLastName("defendant")
            .recordingVersionNumber(2)
            .build();
        CSVArchiveListData archive = new CSVArchiveListData();
        String baseKey = "base-key";
        String participantPair = "witness-defendant";
        when(inMemoryCacheService.generateCacheKey(processedRecording.getCaseReference(), participantPair))
            .thenReturn(baseKey);
        when(inMemoryCacheService.getHashValue(baseKey, "recordingMetadata", String.class))
            .thenReturn(null);

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.NO_PARENT_FOUND);
        assertThat(result.getCategory()).isEqualTo(Constants.Reports.FILE_MISSING_DATA);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("Should return success when recording version is 1")
    void validateProcessedRecordingSuccessRecordingVersion1() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("case_reference")
            .recordingVersionNumber(1)
            .witnessFirstName("witness")
            .defendantLastName("defendant")
            .build();
        CSVArchiveListData archive = new CSVArchiveListData();
        String baseKey = "base-key";
        String participantPair = "witness-defendant";
        when(inMemoryCacheService.generateCacheKey(processedRecording.getCaseReference(), participantPair))
            .thenReturn(baseKey);

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCategory()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should return success for recording with version > 1")
    void validateProcessedRecordingSuccessRecordingVersionGT1() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .court(new Court())
            .isMostRecentVersion(true)
            .caseReference("case_reference")
            .recordingVersionNumber(1)
            .witnessFirstName("witness")
            .defendantLastName("defendant")
            .build();
        CSVArchiveListData archive = new CSVArchiveListData();
        String baseKey = "base-key";
        String participantPair = "witness-defendant";
        when(inMemoryCacheService.generateCacheKey(processedRecording.getCaseReference(), participantPair))
            .thenReturn(baseKey);
        when(inMemoryCacheService.getHashValue(baseKey, "recordingMetadata", String.class))
            .thenReturn("some-value");

        ServiceResult<ProcessedRecording> result = dataValidationService.validateProcessedRecording(
            processedRecording,
            archive
        );

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCategory()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }
}
