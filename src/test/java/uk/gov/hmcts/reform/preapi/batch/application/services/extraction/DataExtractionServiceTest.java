package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.PATTERN_MATCH;

@SpringBootTest(classes = { DataExtractionService.class })
public class DataExtractionServiceTest {
    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private MetadataValidator metadataValidator;

    @MockitoBean
    private PatternMatcherService patternMatcherService;

    @Autowired
    private DataExtractionService dataExtractionService;

    @Test
    void processTestValidationFailure() {
        MigrationRecord data = new MigrationRecord();
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        when(metadataValidator.validateTest(data)).thenReturn(ServiceResultUtil.failure("", ""));
        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void processTestValidationFailsPatternMatch() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("sanitizedArchiveName.mp4");
        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName())).thenReturn(Optional.empty());

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(PATTERN_MATCH);
        assertThat(result.getCategory()).isEqualTo(VfFailureReason.VALIDATION_FAILED.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processTestValidationMatchesTestPattern() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("sanitizedArchiveName.mp4");
        data.setDuration(10);
        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("UUID Pattern", mock(Matcher.class))));

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isTest()).isTrue();
        assertThat(result.getTestItem()).isNotNull();
        assertThat(result.getTestItem().getArchiveItem()).isEqualTo(data);
        assertThat(result.getTestItem().getReason()).isEqualTo("Matched TEST regex pattern");
        assertThat(result.getTestItem().isDurationCheck()).isEqualTo(false);
        assertThat(result.getTestItem().getDurationInSeconds()).isEqualTo(10);
        assertThat(result.getTestItem().isKeywordCheck()).isEqualTo(false);
        assertThat(result.getTestItem().getKeywordFound()).isEqualTo("[]");
        assertThat(result.getTestItem().isRegexFailure()).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processTestValidationFailExtensionCheck() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("sanitizedArchiveName.mp4");
        data.setDuration(10);

        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");

        var extensionResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        when(extensionResult.isSuccess()).thenReturn(false);
        when(metadataValidator.validateExtension("exe")).thenReturn(extensionResult);

        Matcher matcher = mock(Matcher.class);
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("Standard", matcher)));
        when(matcher.group("ext")).thenReturn("exe");
        when(matcher.group("defendantLastName")).thenReturn("Defendant");
        when(matcher.group("witnessFirstName")).thenReturn("Witness");

        when(metadataValidator.validateExtractedMetadata(any(ExtractedMetadata.class)))
            .thenReturn(extensionResult);

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isEqualTo(extensionResult);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processTestValidationMetadataFailure() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("sanitizedArchiveName.mp4");
        data.setDuration(10);
        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");

        var extensionResult = mock(ServiceResult.class);
        when(extensionResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtension("mp4")).thenReturn(extensionResult);

        Matcher matcher = mock(Matcher.class);
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("Standard", matcher)));
        when(matcher.group("ext")).thenReturn("mp4");
        when(matcher.group("defendantLastName")).thenReturn("Defendant");
        when(matcher.group("witnessFirstName")).thenReturn("Witness");

        var metadataResult = mock(ServiceResult.class);
        when(metadataResult.isSuccess()).thenReturn(false);
        when(metadataValidator.validateExtractedMetadata(any(ExtractedMetadata.class))).thenReturn(metadataResult);

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isEqualTo(metadataResult);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processTestValidationSuccess() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("sanitizedArchiveName.mp4");
        data.setDuration(10);
        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");

        var extensionResult = mock(ServiceResult.class);
        when(extensionResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtension("mp4")).thenReturn(extensionResult);

        Matcher matcher = mock(Matcher.class);
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("Standard", matcher)));
        when(matcher.group("ext")).thenReturn("mp4");
        when(matcher.group("defendantLastName")).thenReturn("Defendant");
        when(matcher.group("witnessFirstName")).thenReturn("Witness");

        var metadataResult = mock(ServiceResult.class);
        when(metadataResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtractedMetadata(any(ExtractedMetadata.class))).thenReturn(metadataResult);

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void processPreExistingValidationFailure() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("some-file-PRE-existing.mp4");

        when(metadataValidator.validatePreExisting(any(MigrationRecord.class)))
            .thenReturn(ServiceResultUtil.failure("Keyword 'PRE' found", VfFailureReason.PRE_EXISTING.toString()));

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Keyword 'PRE' found");
        verify(metadataValidator).validatePreExisting(data);
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractMetaDataDefaultsVersionNumberTo1ForOrigType() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("test-ORIG.mp4");
        data.setDuration(10);

        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");

        var extensionResult = mock(ServiceResult.class);
        when(extensionResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtension("mp4")).thenReturn(extensionResult);

        Matcher matcher = mock(Matcher.class);
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("Standard", matcher)));
        when(matcher.group("ext")).thenReturn("mp4");
        when(matcher.group("defendantLastName")).thenReturn("Defendant");
        when(matcher.group("witnessFirstName")).thenReturn("Witness");
        when(matcher.group("versionType")).thenReturn("ORIG");
        when(matcher.group("versionNumber")).thenReturn(null);

        var metadataResult = mock(ServiceResult.class);
        when(metadataResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtractedMetadata(any(ExtractedMetadata.class))).thenReturn(metadataResult);

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isTrue();
        verify(metadataValidator).validateExtractedMetadata(argThat(metadata ->
            metadata.getRecordingVersionNumber().equals("1")
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractMetaDataDefaultsVersionNumberTo1ForCopyType() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("test-COPY.mp4");
        data.setDuration(10);

        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");

        var extensionResult = mock(ServiceResult.class);
        when(extensionResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtension("mp4")).thenReturn(extensionResult);

        Matcher matcher = mock(Matcher.class);
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("Standard", matcher)));
        when(matcher.group("ext")).thenReturn("mp4");
        when(matcher.group("defendantLastName")).thenReturn("Defendant");
        when(matcher.group("witnessFirstName")).thenReturn("Witness");
        when(matcher.group("versionType")).thenReturn("COPY");
        when(matcher.group("versionNumber")).thenReturn("");

        var metadataResult = mock(ServiceResult.class);
        when(metadataResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtractedMetadata(any(ExtractedMetadata.class))).thenReturn(metadataResult);

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isTrue();
        verify(metadataValidator).validateExtractedMetadata(argThat(metadata ->
            metadata.getRecordingVersionNumber().equals("1")
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractMetaDataHandlesMatcherGroupException() {
        MigrationRecord data = new MigrationRecord();
        data.setArchiveName("test.mp4");
        data.setDuration(10);

        var testResult = mock(ServiceResult.class);
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validatePreExisting(any(MigrationRecord.class));
        doReturn(ServiceResultUtil.success(data))
            .when(metadataValidator).validateRawFile(any(MigrationRecord.class));
        when(testResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateTest(any(MigrationRecord.class))).thenReturn(testResult);
        when(metadataValidator.parseExtension(data.getSanitizedArchiveName())).thenReturn("mp4");

        var extensionResult = mock(ServiceResult.class);
        when(extensionResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtension("mp4")).thenReturn(extensionResult);

        Matcher matcher = mock(Matcher.class);
        when(patternMatcherService.findMatchingPattern(data.getSanitizedArchiveName()))
            .thenReturn(Optional.of(Map.entry("Standard", matcher)));
        when(matcher.group("ext")).thenReturn("mp4");
        when(matcher.group("defendantLastName")).thenReturn("Defendant");
        when(matcher.group("witnessFirstName")).thenReturn("Witness");
        when(matcher.group("versionType")).thenReturn("ORIG");
        when(matcher.group("versionNumber")).thenReturn("2");
        when(matcher.group("court")).thenThrow(new IllegalStateException("Group not found"));

        var metadataResult = mock(ServiceResult.class);
        when(metadataResult.isSuccess()).thenReturn(true);
        when(metadataValidator.validateExtractedMetadata(any(ExtractedMetadata.class))).thenReturn(metadataResult);

        ServiceResult<?> result = dataExtractionService.process(data);

        assertThat(result.isSuccess()).isTrue();
        verify(metadataValidator).validateExtractedMetadata(any(ExtractedMetadata.class));
    }
}
