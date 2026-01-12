package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.PREDATES_GO_LIVE;

@SpringBootTest(classes = {MetadataValidator.class})
public class MetadataValidatorTest {
    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private MetadataValidator metadataValidator;

    @Mock
    private MigrationRecord archiveItem;

    @Test
    void validateTestFailureWhenArchivePredatesGoLive() {
        when(archiveItem.getSanitizedArchiveName()).thenReturn("archive.csv");
        when(archiveItem.getArchiveName()).thenReturn("archive.csv");
        when(archiveItem.getCreateTimeAsLocalDateTime())
            .thenReturn(Constants.GO_LIVE_DATE.minusMonths(1).atStartOfDay());

        ServiceResult<?> result = metadataValidator.validateTest(archiveItem);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(PREDATES_GO_LIVE);
        verify(loggingService, times(1)).logError(PREDATES_GO_LIVE, "archive.csv");
    }

    @Test
    void validateTestTestResultWhenArchiveIsTestRecording() {
        when(archiveItem.getArchiveName()).thenReturn("archive.csv");
        when(archiveItem.getSanitizedArchiveName()).thenReturn("archive_test.csv");
        when(archiveItem.getDuration()).thenReturn(1000);
        when(archiveItem.getCreateTimeAsLocalDateTime())
            .thenReturn(Timestamp.from(Instant.now()).toLocalDateTime());


        ServiceResult<?> result = metadataValidator.validateTest(archiveItem);

        assertThat(result.getTestItem()).isNotNull();
    }

    @Test
    void validateTestSuccessWhenArchivePassesValidationAndIsNotTest() {
        when(archiveItem.getSanitizedArchiveName()).thenReturn("valid.csv");
        when(archiveItem.getArchiveName()).thenReturn("archive.csv");
        when(archiveItem.getDuration()).thenReturn(1000);
        when(archiveItem.getCreateTimeAsLocalDateTime())
            .thenReturn(Timestamp.from(Instant.now()).toLocalDateTime());

        ServiceResult<?> result = metadataValidator.validateTest(archiveItem);

        assertThat(result.isSuccess()).isTrue();
        verify(loggingService, times(1)).logDebug("Passed test validation", archiveItem.getSanitizedArchiveName());
    }

    @Test
    void validateExtensionFailureForNullExtension() {
        ServiceResult<?> result = metadataValidator.validateExtension(null);

        assertThat(result.isSuccess()).isFalse();

        verify(loggingService, times(1)).logError("File extension is missing or null for archive.");
    }

    @Test
    void validateExtensionFailureForEmptyExtension() {
        ServiceResult<?> result = metadataValidator.validateExtension("");

        assertThat(result.isSuccess()).isFalse();

        verify(loggingService, times(1)).logError("File extension is missing or null for archive.");
    }

    @Test
    void validateExtensionFailureForInvalidExtension() {
        ServiceResult<?> result = metadataValidator.validateExtension("exe");

        assertThat(result.isSuccess()).isFalse();

        verify(loggingService, times(1))
            .logDebug("Invalid file extension: %s | Allowed extensions: %s", "exe", Constants.VALID_EXTENSIONS);
    }

    @Test
    void validateExtensionSuccessForValidExtension() {
        ServiceResult<?> result = metadataValidator.validateExtension("mp4");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateExtractedMetadataFailureMetadataNull() {
        ServiceResult<?> result = metadataValidator.validateExtractedMetadata(null);

        assertThat(result.isSuccess()).isFalse();

        verify(loggingService, times(1))
            .logError("Missing required metadata fields: %s", "Metadata object is null");
    }

    @Test
    void validateExtractedMetadataSuccess() {
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setCourtReference("court");
        extractedMetadata.setUrn("urn");
        extractedMetadata.setExhibitReference("exhibit");
        extractedMetadata.setDefendantLastName("defendant");
        extractedMetadata.setWitnessFirstName("witness");
        extractedMetadata.setRecordingVersion("1");
        extractedMetadata.setFileExtension("mp4");

        ServiceResult<?> result = metadataValidator.validateExtractedMetadata(extractedMetadata);

        assertThat(result.isSuccess()).isTrue();

        verify(loggingService, never()).logError(anyString(), anyString());
    }

    @Test
    void isValidDurationTrue() {
        MigrationRecord data = new MigrationRecord();
        data.setDuration(10);

        assertThat(metadataValidator.isValidDuration(data)).isTrue();
    }

    @Test
    void isValidDurationFalse() {
        MigrationRecord data = new MigrationRecord();
        data.setDuration(9);

        assertThat(metadataValidator.isValidDuration(data)).isFalse();
    }

    @Test
    void getMissingMetadataFieldsDataIsNull() {
        List<String> results = metadataValidator.getMissingMetadataFields(null);

        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo("Metadata object is null");
    }

    @Test
    void getMissingMetadataFieldsDataHasMissingFields() {
        List<String> results = metadataValidator.getMissingMetadataFields(new ExtractedMetadata());

        assertThat(results).isNotNull();
        assertThat(results).hasSize(5);
        assertThat(results.stream().anyMatch(e -> e.equals("Court Reference"))).isTrue();
        assertThat(results.stream().anyMatch(e -> e.equals("URN and Exhibit Reference"))).isTrue();
        assertThat(results.stream().anyMatch(e -> e.equals("Defendant Last Name"))).isTrue();
        assertThat(results.stream().anyMatch(e -> e.equals("Witness First Name"))).isTrue();
        assertThat(results.stream().anyMatch(e -> e.equals("Recording Version"))).isTrue();
        // assertThat(results.stream().anyMatch(e -> e.equals("File Extension"))).isTrue();
    }

    @Test
    void parseExtensionIsMp4() {
        assertThat(metadataValidator.parseExtension("file.mp4")).isEqualTo("mp4");
    }

    @Test
    void parseExtensionIsNotMp4() {
        assertThat(metadataValidator.parseExtension(null)).isEqualTo("");
        assertThat(metadataValidator.parseExtension("file.exe")).isEqualTo("");
    }

    @Test
    void validatePreExistingFails() {
        when(archiveItem.getArchiveName()).thenReturn("Amersh-210301-XYZ-PRE-123.mp4");

        ServiceResult<?> result = metadataValidator.validatePreExisting(archiveItem);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Keyword 'PRE' found");
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.PRE_EXISTING.toString());
    }

    @Test
    void validatePreExistingSucceeds() {
        when(archiveItem.getArchiveName()).thenReturn("Amersh-210301-XYZ-ORIG1.mp4");

        ServiceResult<?> result = metadataValidator.validatePreExisting(archiveItem);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateRawFileFails() {
        when(archiveItem.getArchiveName()).thenReturn("some/path/recording.RAW");

        ServiceResult<?> result = metadataValidator.validateRawFile(archiveItem);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(Constants.ErrorMessages.RAW_FILE);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.RAW_FILES.toString());
    }

    @Test
    void validateRawFileSucceeds() {
        when(archiveItem.getArchiveName()).thenReturn("some/path/recording.mp4");

        ServiceResult<?> result = metadataValidator.validateRawFile(archiveItem);

        assertThat(result.isSuccess()).isTrue();
    }
}
