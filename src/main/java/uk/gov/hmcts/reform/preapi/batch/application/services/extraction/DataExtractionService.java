package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

@Service
public class DataExtractionService {
    private LoggingService loggingService;
    private PatternMatcherService patternMatcher;

    public DataExtractionService(
        LoggingService loggingService,
        PatternMatcherService patternMatcher
    ) {
        this.loggingService = loggingService;
        this.patternMatcher = patternMatcher;
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
        Optional<Map.Entry<String, Matcher>> patternMatch = patternMatcher.findMatchingPattern(archiveItem.getArchiveName());

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
