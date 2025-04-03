package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.regex.Matcher;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.INVALID_FILE_EXTENSION;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.PATTERN_MATCH;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.Reports.FILE_INVALID_FORMAT;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.Reports.FILE_REGEX;

@Service
public class DataExtractionService {
    private final LoggingService loggingService;
    private final MetadataValidator validator;
    private final PatternMatcherService patternMatcher;

    public DataExtractionService(
        LoggingService loggingService,
        MetadataValidator validator,
        PatternMatcherService patternMatcher
    ) {
        this.loggingService = loggingService;
        this.validator = validator;
        this.patternMatcher = patternMatcher;
    }

    public ServiceResult<?> process(CSVArchiveListData archiveItem) {
        loggingService.logDebug("Starting data extraction for: %s", archiveItem.getSanitizedArchiveName());

        if (archiveItem.getSanitizedArchiveName().isEmpty()) {
            loggingService.logWarning("Sanitized archive name is missing for: %s", archiveItem.getArchiveName());
        }
        // -- 1. TEST validation (validate for pre-go-live, duration check and test keywords)
        ServiceResult<?> validationResult = validator.validateTest(archiveItem);
        loggingService.logDebug("Validation result in extraction %s", validationResult.isSuccess());
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        // --2 Check file extension for .mp4
        String sanitisedName = archiveItem.getSanitizedArchiveName();
        String ext = validator.parseExtension(sanitisedName);
        if (ext.isBlank()) {
            return ServiceResultUtil.failure(INVALID_FILE_EXTENSION, FILE_INVALID_FORMAT);
        }

        // -- 3. Pattern matching for legitamite and test scenarios
        var patternMatch = patternMatcher.findMatchingPattern(sanitisedName);
        loggingService.logDebug("Pattern match: %s", patternMatch);
        if (patternMatch.isEmpty()) {
            loggingService.logDebug("No pattern matched for file: %s", archiveItem);
            return ServiceResultUtil.failure(PATTERN_MATCH, FILE_REGEX);
        }

        loggingService.logDebug("Checking for test pattern");
        if (RegexPatterns.TEST_PATTERNS.containsKey(patternMatch.get().getKey())) {
            loggingService.logError(
                "Test pattern match found for file: %s | Pattern: %s",
                archiveItem.getSanitizedArchiveName(), patternMatch.get().getKey()
            );
            var testItem = new TestItem(
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
        var extractedData = extractMetaData(matcher, archiveItem);

        loggingService.logDebug("Extracted metadata in extraction service: " + extractedData);

        // Validate file extension
        ServiceResult<?> extensionCheckResult = validator.validateExtension(extractedData.getFileExtension());
        if (!extensionCheckResult.isSuccess()) {
            return extensionCheckResult;
        }

        // Validate metadata failure
        ServiceResult<?> metadataCheckResult = validator.validateExtractedMetadata(extractedData);
        return !metadataCheckResult.isSuccess()
            ? metadataCheckResult
            : ServiceResultUtil.success(extractedData);
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

    private ExtractedMetadata extractMetaData(Matcher matcher, CSVArchiveListData archiveItem) {
        return new ExtractedMetadata(
            getMatcherGroup(matcher, "court"),
            getMatcherGroup(matcher, "urn"),
            getMatcherGroup(matcher, "exhibitRef"),
            getMatcherGroup(matcher, "defendantLastName"),
            getMatcherGroup(matcher, "witnessFirstName"),
            getMatcherGroup(matcher, "versionType"),
            getMatcherGroup(matcher, "versionNumber"),
            getMatcherGroup(matcher, "ext"),
            archiveItem.getCreateTimeAsLocalDateTime(),
            archiveItem.getDuration(),
            archiveItem.getFileName(),
            archiveItem.getFileSize(),
            archiveItem.getArchiveName()
        );
    }
}
