package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.PATTERN_MATCH;

@Service
public class DataExtractionService {
    private final LoggingService loggingService;
    private final MetadataValidator validator;
    private final PatternMatcherService patternMatcher;

    public DataExtractionService(final LoggingService loggingService,
                                 final MetadataValidator validator,
                                 final PatternMatcherService patternMatcher) {
        this.loggingService = loggingService;
        this.validator = validator;
        this.patternMatcher = patternMatcher;
    }

    public ServiceResult<?> process(MigrationRecord archiveItem) {
        loggingService.logDebug("Extracting metadata...");

        if (archiveItem.getSanitizedArchiveName().isEmpty()) {
            loggingService.logWarning("Missing sanitized name");
        }

        ServiceResult<?> preExistingValidation = validator.validatePreExisting(archiveItem);
        if (!preExistingValidation.isSuccess()) {
            return preExistingValidation;
        }

        // -- 1. TEST validation (validate for pre-go-live, duration check and test keywords)
        ServiceResult<?> testValidationResult = validator.validateTest(archiveItem);
        loggingService.logDebug("Validation result in extraction %s", testValidationResult.isSuccess());
        if (!testValidationResult.isSuccess()) {
            return testValidationResult;
        }

        ServiceResult<?> testValidationExtensionResult = validator.validateRawFile(archiveItem);
        if (!testValidationExtensionResult.isSuccess()) {
            return testValidationExtensionResult;
        }

        String archiveName = archiveItem.getArchiveName();

        // -- 2. Pattern matching for legitimate and test scenarios
        String sanitisedName = archiveItem.getSanitizedArchiveName();
        Optional<Map.Entry<String, Matcher>> patternMatch = patternMatcher.findMatchingPattern(sanitisedName);
        if (patternMatch.isEmpty()) {
            loggingService.logDebug("Extraction - No pattern matched: archiveName=%s", archiveName);
            return ServiceResultUtil.failure(PATTERN_MATCH, VfFailureReason.VALIDATION_FAILED.toString());
        }
        loggingService.logDebug(
            "Extraction - Matching patterns for archiveName=%s, pattern=%s", archiveName, patternMatch);

        if (RegexPatterns.TEST_PATTERNS.containsKey(patternMatch.get().getKey())) {
            loggingService.logInfo("Extraction - Test pattern match: %s", patternMatch.get().getKey());
            TestItem testItem = new TestItem(
                archiveItem,
                "Matched TEST regex pattern",
                false,
                archiveItem.getDuration(),
                false,
                "[]",
                true

            );
            return ServiceResultUtil.test(testItem, true);
        }

        Matcher matcher = patternMatch.get().getValue();
        ExtractedMetadata extractedData = extractMetaData(matcher, archiveItem);
        String archiveId = archiveItem.getArchiveId();

        loggingService.logDebug("Extraction - Metadata extracted: " + extractedData);

        // Validate metadata failure
        ServiceResult<?> metadataCheckResult = validator.validateExtractedMetadata(extractedData);
        if (!metadataCheckResult.isSuccess()) {
            loggingService.logWarning("Extraction - Metadata validation failed: archiveId=%s, error=%s",
                archiveId, metadataCheckResult.getErrorMessage());
            return metadataCheckResult;
        }

        loggingService.logInfo(
            "Extraction - Completed successfully: archiveId=%s, archiveName=%s", archiveId, archiveName);
        return ServiceResultUtil.success(extractedData);
    }

    // =========================
    // Metadata Extraction
    // =========================
    private String getMatcherGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (Exception e) {
            return "";
        }
    }

    private ExtractedMetadata extractMetaData(Matcher matcher, MigrationRecord archiveItem) {
        String versionType = getMatcherGroup(matcher, "versionType");
        String versionNumber = getMatcherGroup(matcher, "versionNumber");
        versionType = RecordingUtils.normalizeVersionType(versionType);

        if (Constants.VALID_ORIG_TYPES.contains(versionType.toUpperCase(Locale.UK))
            && (versionNumber == null || versionNumber.isEmpty())) {
            versionNumber = "1";
        }

        return new ExtractedMetadata(
            getMatcherGroup(matcher, "court"),
            null,
            getMatcherGroup(matcher, "urn"),
            getMatcherGroup(matcher, "exhibitRef"),
            getMatcherGroup(matcher, "defendantLastName"),
            getMatcherGroup(matcher, "witnessFirstName"),
            versionType,
            versionNumber,
            getMatcherGroup(matcher, "ext"),
            archiveItem.getCreateTimeAsLocalDateTime(),
            archiveItem.getDuration(),
            archiveItem.getFileName(),
            archiveItem.getFileSizeMb(),
            archiveItem.getArchiveId(),
            archiveItem.getArchiveName()
        );
    }
}
