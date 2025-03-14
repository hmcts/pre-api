package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

@Service
public class DataExtractionService {
    private LoggingService loggingService;
    private MetadataValidator validator;
    private PatternMatcherService patternMatcher;

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
        // pre-extraction validation
        if (archiveItem == null) {
            loggingService.logError("Failed to process: archiveItem is null");
            return ServiceResultUtil.failure("Failed to process: archiveItem is null", 
                Constants.Reports.FILE_MISSING_DATA);
        }

        if (!validator.isDateAfterGoLive(archiveItem)) {
            // loggingService.logError(Constants.ErrorMessages.PREDATES_GO_LIVE, archiveItem.getArchiveName());
            return ServiceResultUtil.failure(Constants.ErrorMessages.PREDATES_GO_LIVE, 
                Constants.Reports.FILE_PRE_GO_LIVE);
        }

        ServiceResult<TestItem> testValidationResult = validateTest(archiveItem);
        if (testValidationResult != null) {
            return testValidationResult;
        }

        String sanitizedArchiveName = cleanArchiveName(archiveItem.getArchiveName());
        loggingService.logDebug("Sanitized name: "+ sanitizedArchiveName);
        Optional<Map.Entry<String, Matcher>> patternMatch = patternMatcher.findMatchingPattern(sanitizedArchiveName);

        if (patternMatch.isEmpty()) {
            loggingService.logError(Constants.ErrorMessages.PATTERN_MATCH, archiveItem.getArchiveName());
        }

        Matcher matcher = patternMatch.get().getValue();
        String patternName = patternMatch.get().getKey();
        loggingService.logDebug(
            "Extracting metadata using pattern %s for file: %s",
            patternName,
            archiveItem.getArchiveName()
        );
        
        var extractedData = extractMetaData(matcher, archiveItem, sanitizedArchiveName);

        loggingService.logDebug("Extracted metadata in extraction service: " + extractedData);
        if (extractedData == null){
            return ServiceResultUtil.failure(Constants.ErrorMessages.PATTERN_MATCH, 
                "Regex_Issues");
        }
       
        if (!isValidExtension(extractedData.getFileExtension())) {
            return ServiceResultUtil.failure(Constants.ErrorMessages.INVALID_FILE_EXTENSION, 
                Constants.Reports.FILE_INVALID_FORMAT);
        }

        List<String> missingFields = validator.getMissingMetadataFields(extractedData);
        if (!missingFields.isEmpty()) {
            return ServiceResultUtil.failure(
                "Missing required metadata fields: " + String.join(", ", missingFields),
                "Invalid Metadata"
            );
        }

        
        // if (!validator.hasRequiredMetadata(extractedData)) {
        //     return ServiceResultUtil.failure("Missing required metadata fields", "Invalid Metadata");
        // }

       

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

    private ExtractedMetadata extractMetaData(Matcher matcher, CSVArchiveListData archiveItem, String sanitizedName) {
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
            archiveItem.getFileSize(),
            sanitizedName
        );
    }
    // =========================
    // Validation
    // =========================

    private ServiceResult<TestItem> validateTest(CSVArchiveListData archiveItem) {
        boolean keywordCheck = !isTest(archiveItem);
        boolean durationCheck = !validator.isValidDuration(archiveItem);
        StringBuilder failureReasons = new StringBuilder();

        if (keywordCheck) {
            failureReasons.append(Constants.ErrorMessages.TEST_ITEM_NAME).append("; ");
        }

        if (durationCheck) {
            failureReasons.append(Constants.ErrorMessages.TEST_DURATION).append("; ");
        }

        if (failureReasons.length() > 0) {
            String keywordFound = keywordCheck ? extractTestKeywords(archiveItem.getArchiveName()) : "N/A";
            
            TestItem testItem = new TestItem(
                archiveItem,
                failureReasons.toString().trim(),
                durationCheck,
                archiveItem.getDuration(),
                keywordCheck,
                keywordFound
            );

            loggingService.logError("Test validation failed: %s | File: %s",
                failureReasons.toString().trim(), archiveItem.getArchiveName());

            return ServiceResultUtil.test(testItem, true);  
        }

        return null;
    }


    private boolean isValidExtension(String ext) {
        boolean isValid = ext != null && Constants.VALID_EXTENSIONS.contains(ext);
        if (!isValid) {
            loggingService.logDebug("Invalid file extension: %s", ext);
        }
        return isValid;
    }

    private boolean isTest(CSVArchiveListData archiveItem) {
        String lowerName = archiveItem.getArchiveName().toLowerCase();
        for (String keyword : Constants.TEST_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private String extractTestKeywords(String archiveName) {
        if (archiveName == null || archiveName.isBlank()) {
            return "N/A";
        }

        List<String> foundKeywords = new ArrayList<>();

        for (String keyword : Constants.TEST_KEYWORDS) {
            if (archiveName.toLowerCase().contains(keyword.toLowerCase())) {
                foundKeywords.add(keyword);
            }
        }

        return foundKeywords.isEmpty() ? "N/A" : foundKeywords.toString();
    }


   
    // =========================
    // Utility: Archive Name Cleaning
    // =========================

    private static String cleanArchiveName(String archiveName) {
        return archiveName
            .replaceAll("^QC[_\\d]?", "")
            .replaceAll("^QC(?![A-Za-z])", "")
            .replaceAll("[-_\\s]QC\\d*(?=\\.[a-zA-Z0-9]+$|$)", "")
            .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
            .replaceAll("_(?=\\.[^.]+$)", "")
            .replaceAll("[-_\\s]{2,}", "-")
            .trim();
    }
}
