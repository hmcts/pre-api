package uk.gov.hmcts.reform.preapi.batch.application.services.extraction;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MetadataValidator {
    private final LoggingService loggingService;

    public MetadataValidator(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    /**
     * Validates if the archive item's date is after the go-live date.
     */
    public boolean isDateAfterGoLive(CSVArchiveListData archiveItem) {
        Optional<LocalDateTime> recordingTimestamp = Optional.ofNullable(archiveItem.getCreateTimeAsLocalDateTime());

        if (recordingTimestamp.isEmpty()) {
            loggingService.logError("Failed to extract date for file: %s | Raw createTime: %s",
                archiveItem.getArchiveName(), archiveItem.getCreateTime());
            return false;
        }

        boolean isAfterGoLive = !recordingTimestamp.get().toLocalDate().isBefore(Constants.GO_LIVE_DATE);

        if (!isAfterGoLive) {
            loggingService.logWarning("File predates go-live date: %s | Extracted Date: %s", 
                archiveItem.getArchiveName(), recordingTimestamp.get());
        }

        return isAfterGoLive;
    }

    /**
     * Checks if the file duration meets the minimum required threshold.
     */
    public boolean isValidDuration(CSVArchiveListData archiveItem) {
        int duration = archiveItem.getDuration();

        if (duration < Constants.MIN_RECORDING_DURATION) {
            loggingService.logWarning("File duration too short: %s | Duration: %d sec (Min Required: %d sec)",
                archiveItem.getArchiveName(), duration, Constants.MIN_RECORDING_DURATION);
            return false;
        }
        return true;
    }

    /**
     * Checks if the file extension is valid.
     */
    public boolean isValidExtension(String ext) {
        if (isEmpty(ext)) {
            loggingService.logWarning("File extension is missing.");
            return false;
        }

        boolean isValid = Constants.VALID_EXTENSIONS.contains(ext.toLowerCase());

        if (!isValid) {
            loggingService.logWarning("Invalid file extension: %s | Allowed extensions: %s",
                ext, Constants.VALID_EXTENSIONS);
        }

        return isValid;
    }

 
    public boolean hasRequiredMetadata(ExtractedMetadata metadata) {
        if (metadata == null) {
            loggingService.logWarning("Metadata validation failed: Metadata object is null.");
            return false;
        }

        boolean isValid = isNonEmpty(metadata.getCourtReference()) 
                && (isNonEmpty(metadata.getUrn()) || isNonEmpty(metadata.getExhibitReference())) 
                && isNonEmpty(metadata.getDefendantLastName())
                && isNonEmpty(metadata.getWitnessFirstName()) 
                && isNonEmpty(metadata.getRecordingVersion()) 
                && isNonEmpty(metadata.getFileExtension());

        if (!isValid) {
            loggingService.logWarning("Metadata validation failed for file: %s | Missing required fields.",
                metadata.getFileName());
        }

        return isValid;
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private boolean isNonEmpty(String value) {
        return value != null && !value.isBlank();
    }
}
