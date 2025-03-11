package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.RegexPatterns;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExtractionService {
    private LoggingService loggingService;
    private static final Map<String, Pattern> NAMED_PATTERNS = initializePatterns();

    public DataExtractionService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public ServiceResult<ExtractedMetadata> process(CSVArchiveListData archiveItem) {
        // pre-extraction validation
        if (archiveItem == null) {
            loggingService.logError("Received null archiveItem");
            return ServiceResultUtil.failure("Failed to process: archiveItem is null", "Invalid Data");
        }

        if (!isDateAfterGoLive(archiveItem)) {
            loggingService.logError(Constants.ErrorMessages.PREDATES_GO_LIVE, archiveItem.getArchiveName());
            return ServiceResultUtil.failure(Constants.ErrorMessages.PREDATES_GO_LIVE, "Pre-Go-Live");
        }

        if (!isTest(archiveItem)) {
            loggingService.logError(Constants.ErrorMessages.TEST_ITEM_NAME, archiveItem.getArchiveName());
            return ServiceResultUtil.failure(Constants.ErrorMessages.TEST_ITEM_NAME, "Test");
        }

        if (!isValidDuration(archiveItem)) {
            loggingService.logError(Constants.ErrorMessages.TEST_DURATION, archiveItem.getArchiveName());
            return ServiceResultUtil.failure(Constants.ErrorMessages.TEST_DURATION, "Test");
        }

        // find a pattern match
        Optional<Map.Entry<String, Matcher>> patternMatch = matchPattern(archiveItem);

        if (patternMatch.isEmpty()) {
            loggingService.logError(Constants.ErrorMessages.PATTERN_MATCH, archiveItem.getArchiveName());
            throw new IllegalStateException(Constants.ErrorMessages.PATTERN_MATCH);
        }

        Matcher matcher = patternMatch.get().getValue();
        String patternName = patternMatch.get().getKey();
        loggingService.logDebug(
            "Extracting metadata using pattern %s for file: %s",
            patternName,
            archiveItem.getArchiveName()
        );

        var extractedData = new ExtractedMetadata(
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
        return ServiceResultUtil.success(extractedData);
    }

    // =========================
    // Pattern Management
    // =========================

    private static Map<String, Pattern> initializePatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        patterns.put("Standard", RegexPatterns.STANDARD_PATTERN);
        patterns.put("StandardWithNumbers", RegexPatterns.STANDARD_PATTERN_WITH_NUMBERS);
        patterns.put("SpecificT", RegexPatterns.SPECIFIC_T_PATTERN);
        patterns.put("SpecialCase", RegexPatterns.SPECIAL_CASE_PATTERN);
        patterns.put("DoubleURN", RegexPatterns.DOUBLE_URN_NO_EXHIBIT_PATTERN);
        patterns.put("DoubleExhibit", RegexPatterns.DOUBLE_EXHIBIT_NO_URN_PATTERN);
        patterns.put("Prefix", RegexPatterns.PREFIX_PATTERN);
        patterns.put("Flexible", RegexPatterns.FLEXIBLE_PATTERN);
        return Collections.unmodifiableMap(patterns);
    }

    // =========================
    // Pattern Matching
    // =========================

    public Optional<Map.Entry<String, Matcher>> matchPattern(CSVArchiveListData archiveItem) {
        loggingService.logDebug("ARCHIVE ITEM: %s", archiveItem);
        if (archiveItem == null || archiveItem.getArchiveName() == null) {
            loggingService.logWarning("Invalid archive item or name");
            return Optional.empty();
        }

        String cleanedArchiveName = cleanArchiveName(archiveItem.getArchiveName());
        loggingService.logDebug("Cleaned archive name: %s", cleanedArchiveName);

        for (Map.Entry<String, Pattern> entry : NAMED_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(cleanedArchiveName);
            if (matcher.matches()) {
                loggingService.logDebug(
                    "Pattern %s matched for file: %s",
                    entry.getKey(),
                    archiveItem.getArchiveName()
                );
                return Optional.of(Map.entry(entry.getKey(), matcher));
            }
        }

        loggingService.logWarning("No pattern match found for file: %s", archiveItem.getArchiveName());
        return Optional.empty();
    }

    // =========================
    // Metadata Extraction
    // =========================

    private String getMatcherGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (Exception e) {
            loggingService.logDebug("Group %s not found in pattern for file.", groupName);
            return "";
        }
    }

    // =========================
    // Validation
    // =========================

    private boolean isDateAfterGoLive(CSVArchiveListData archiveItem) {
        LocalDateTime recordingTimestamp = archiveItem.getCreateTimeAsLocalDateTime();

        if (recordingTimestamp == null) {
            loggingService.logError(
                "Failed to extract date for %s | Raw createTime: %s",
                archiveItem.getArchiveName(), archiveItem.getCreateTime()
            );
            return false;
        }

        LocalDate recordingDate = recordingTimestamp.toLocalDate();
        boolean isAfterGoLive = !recordingDate.isBefore(Constants.GO_LIVE_DATE);

        return isAfterGoLive;
    }

    public boolean validateMetadata(ExtractedMetadata metadata) {
        if (metadata == null) {
            loggingService.logWarning("Metadata is null");
            return false;
        }

        boolean isValid = isNonEmpty(metadata.getCourtReference())
            && (isNonEmpty(metadata.getUrn()) || isNonEmpty(metadata.getExhibitReference()))
            && isNonEmpty(metadata.getDefendantLastName())
            && isNonEmpty(metadata.getWitnessFirstName())
            && isValidVersion(metadata.getRecordingVersion(), metadata.getRecordingVersionNumber())
            && isValidExtension(metadata.getFileExtension());

        if (!isValid) {
            loggingService.logWarning("Metadata validation failed for file: %s", metadata.getFileName());
        }

        return isValid;
    }

    private boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isValidVersion(String versionType, String versionNumber) {
        if (!Constants.VALID_VERSION_TYPES.contains(versionType)) {
            loggingService.logDebug("Invalid version type: %s", versionType);
            return false;
        }

        return true;
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

    private boolean isValidDuration(CSVArchiveListData archiveItem) {
        if (archiveItem.getDuration() < Constants.MIN_RECORDING_DURATION) {
            return false;
        }
        return true;
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
