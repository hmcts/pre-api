package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.INVALID_FILE_EXTENSION;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.PREDATES_GO_LIVE;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.TEST_DURATION;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.ErrorMessages.TEST_ITEM_NAME;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.Reports.FILE_INVALID_FORMAT;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.Reports.FILE_MISSING_DATA;
import static uk.gov.hmcts.reform.preapi.batch.config.Constants.Reports.FILE_PRE_GO_LIVE;

@Service
public class MetadataValidator {
    private final LoggingService loggingService;

    public MetadataValidator(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public ServiceResult<?> validateTest(CSVArchiveListData archiveItem) {
        loggingService.logDebug("Validating %s for test", archiveItem.getSanitizedArchiveName());

        if (!isDateAfterGoLive(archiveItem)) {
            loggingService.logError(PREDATES_GO_LIVE, archiveItem.getArchiveName());
            return ServiceResultUtil.failure(PREDATES_GO_LIVE, FILE_PRE_GO_LIVE);
        }

        TestItem test = isTestRecording(archiveItem);
        if (test != null) {
            return ServiceResultUtil.test(test, true);
        }

        loggingService.logDebug("Passed test validation", archiveItem.getSanitizedArchiveName());
        return ServiceResultUtil.success(archiveItem);
    }

    public ServiceResult<?> validateExtension(String extension) {
        if (!isValidExtension(extension)) {
            return ServiceResultUtil.failure(INVALID_FILE_EXTENSION, FILE_INVALID_FORMAT);
        }

        return ServiceResultUtil.success(extension);
    }

    public ServiceResult<?> validateExtractedMetadata(ExtractedMetadata extractedData) {
        List<String> missingFields = getMissingMetadataFields(extractedData);
        if (!missingFields.isEmpty()) {
            loggingService.logError("Missing required metadata fields: %s", String.join(", ", missingFields));
            return ServiceResultUtil.failure(
                "Missing required metadata fields: " + String.join(", ", missingFields),
                FILE_MISSING_DATA
            );
        }

        return ServiceResultUtil.success(extractedData);
    }

    // checks for test (duration and keywords)
    private TestItem isTestRecording(CSVArchiveListData archiveItem) {
        boolean keywordCheck = !hasTestKeywords(archiveItem);
        boolean durationCheck = !isValidDuration(archiveItem);
        StringBuilder failureReasons = new StringBuilder();

        if (keywordCheck) {
            failureReasons.append(TEST_ITEM_NAME).append("; ");
        }

        if (durationCheck) {
            failureReasons.append(TEST_DURATION).append("; ");
        }

        if (!failureReasons.isEmpty()) {
            String keywordFound = keywordCheck ? extractTestKeywords(archiveItem.getArchiveName()) : "N/A";

            TestItem testItem = new TestItem(
                archiveItem,
                failureReasons.toString().trim(),
                durationCheck,
                archiveItem.getDuration(),
                keywordCheck,
                keywordFound,
                false
            );

            loggingService.logError("Test keyword validation failed: %s | Keywords: %s",
                failureReasons.toString().trim(), keywordFound);

            return testItem;
        }

        return null;
    }

    private boolean hasTestKeywords(CSVArchiveListData archiveItem) {
        String lowerName = archiveItem.getSanitizedArchiveName().toLowerCase();
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

    public boolean isValidDuration(CSVArchiveListData archiveItem) {
        int duration = archiveItem.getDuration();

        if (duration < Constants.MIN_RECORDING_DURATION) {
            loggingService.logError("File duration too short: %s | Duration: %d sec (Min Required: %d sec)",
                archiveItem.getArchiveName(), duration, Constants.MIN_RECORDING_DURATION);
            return false;
        }
        return true;
    }

    // Check if the create time pre-dates the go-live date
    public boolean isDateAfterGoLive(CSVArchiveListData archiveItem) {
        Optional<LocalDateTime> recordingTimestamp = Optional.ofNullable(archiveItem.getCreateTimeAsLocalDateTime());

        if (recordingTimestamp.isEmpty()) {
            loggingService.logError("Failed to extract date for file: %s | Raw createTime: %s",
                archiveItem.getArchiveName(), archiveItem.getCreateTime());
            return false;
        }

        boolean isAfterGoLive = !recordingTimestamp.get().toLocalDate().isBefore(Constants.GO_LIVE_DATE);

        if (!isAfterGoLive) {
            loggingService.logError("File predates go-live date: %s | Extracted Date: %s",
                archiveItem.getArchiveName(), recordingTimestamp.get());
        }

        return isAfterGoLive;
    }

    // Check for invalid extensions (not .mp4)
    public boolean isValidExtension(String ext) {
        if (ext == null || ext.isBlank()) {
            loggingService.logError("File extension is missing or null for archive.");
            return false;
        }

        boolean isValid = Constants.VALID_EXTENSIONS.contains(ext.toLowerCase());
        if (!isValid) {
            loggingService.logDebug("Invalid file extension: %s | Allowed extensions: %s",
                ext, Constants.VALID_EXTENSIONS);
        }

        return isValid;
    }

    // Checks for any missing metadata not extracted
    public List<String> getMissingMetadataFields(ExtractedMetadata metadata) {
        List<String> missingFields = new ArrayList<>();

        if (metadata == null) {
            missingFields.add("Metadata object is null");
            return missingFields;
        }
        if (isEmpty(metadata.getCourtReference())) {
            missingFields.add("Court Reference");
        }
        if (isEmpty(metadata.getUrn()) && isEmpty(metadata.getExhibitReference())) {
            missingFields.add("URN and Exhibit Reference");
        }
        if (isEmpty(metadata.getDefendantLastName())) {
            missingFields.add("Defendant Last Name");
        }
        if (isEmpty(metadata.getWitnessFirstName())) {
            missingFields.add("Witness First Name");
        }
        if (isEmpty(metadata.getRecordingVersion())) {
            missingFields.add("Recording Version");
        }
        if (isEmpty(metadata.getFileExtension())) {
            missingFields.add("File Extension");
        }

        return missingFields;
    }

    // Helpers
    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    public String parseExtension(String filename) {
        if (filename == null) {
            return "";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4")) {
            return "mp4";
        }
        return "";
    }


}
