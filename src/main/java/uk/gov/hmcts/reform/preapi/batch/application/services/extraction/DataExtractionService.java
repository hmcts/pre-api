package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.*;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.Reports.*;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

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

        // TEST validation (validate for pre-go-live, duration check and test keywords)
        ServiceResult<?> validationResult = validator.validateTest(archiveItem);
        if (!validationResult.isSuccess()) {
            return validationResult; 
        }

        Optional<Map.Entry<String, Matcher>> patternMatch = patternMatcher.findMatchingPattern(archiveItem.getSanitizedArchiveName());
        if (patternMatch.isEmpty()) {
            return ServiceResultUtil.failure(PATTERN_MATCH, FILE_REGEX);
        }

        if (patternMatch.isPresent() && RegexPatterns.TEST_PATTERNS.containsKey(patternMatch.get().getKey())) {
            loggingService.logError("Test pattern match found for file: %s | Pattern: %s",
                    archiveItem.getSanitizedArchiveName(), patternMatch.get().getKey());
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
        var extractedData = extractMetaData(matcher, archiveItem);

        if (extractedData == null || patternMatch.isEmpty()){
            return ServiceResultUtil.failure(PATTERN_MATCH, FILE_REGEX);
        }

        loggingService.logDebug("Extracted metadata in extraction service: " + extractedData);
       
        // Validate file extension
        ServiceResult<?> extensionCheckResult = validator.validateExtension(extractedData.getFileExtension());
        if (!extensionCheckResult.isSuccess()) {
            return extensionCheckResult;
        }

        // Validate metadata failure
        ServiceResult<?> metadataCheckResult = validator.validateExtractedMetadata(extractedData);
        if (!metadataCheckResult.isSuccess()) {
            return metadataCheckResult;
        }   

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

    private ExtractedMetadata extractMetaData(Matcher matcher, CSVArchiveListData archiveItem) {
        return new ExtractedMetadata(
            getMatcherGroup(matcher, "court"),
            getMatcherGroup(matcher, "date"),
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
            archiveItem.getFileSize()
        );
    }

}
