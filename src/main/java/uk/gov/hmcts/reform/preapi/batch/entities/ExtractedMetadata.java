package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class ExtractedMetadata implements IArchiveData {
    private String courtReference;
    private UUID courtId;
    private String datePattern;
    private String urn;
    private String exhibitReference;
    private String defendantLastName;
    private String witnessFirstName;
    private String recordingVersion;
    private String recordingVersionNumber;
    private String fileExtension;
    private LocalDateTime createTime;
    private int duration;
    private String fileName;
    private String fileSize;
    private String archiveId;
    private String archiveName;
    private String sanitizedArchiveName = "";
    private Timestamp createdAt;

    private static final int MIN_LEN_EXCLUSIVE = 9;
    private static final int MAX_LEN_EXCLUSIVE = 20;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ExtractedMetadata(String courtReference,
                             UUID courtId,
                             String datePattern,
                             String urn,
                             String exhibitReference,
                             String defendantLastName,
                             String witnessFirstName,
                             String recordingVersion,
                             String recordingVersionNumber,
                             String fileExtension,
                             LocalDateTime createTime,
                             int duration,
                             String fileName,
                             String fileSize,
                             String archiveId,
                             String archiveName,
                             Timestamp createdAt) {
        this.courtReference = courtReference;
        this.courtId = courtId;
        this.datePattern = datePattern;
        this.urn = urn != null ? urn.toUpperCase() : null;
        this.exhibitReference = exhibitReference != null ? exhibitReference.toUpperCase() : null;
        this.defendantLastName = formatName(defendantLastName != null ? defendantLastName.toLowerCase() : "");
        this.witnessFirstName = formatName(witnessFirstName != null ? witnessFirstName.toLowerCase() : "");
        this.recordingVersion = recordingVersion;
        this.recordingVersionNumber = recordingVersionNumber;
        this.fileExtension = fileExtension;
        this.createTime = resolveCreateTime(createTime);
        this.duration = duration;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.archiveId = archiveId;
        this.archiveName = archiveName;
        this.createdAt = createdAt;
    }

    private String formatName(String name) {
        if (name == null) {
            return null;
        }

        return Arrays.stream(name.split("(?=[-'\\s])|(?<=[-'\\s])"))
            .map(part -> {
                if (part.matches("[-'\\s]")) {
                    return part;
                } else {
                    return StringUtils.capitalize(part.toLowerCase(Locale.UK));
                }
            })
            .collect(Collectors.joining(""));
    }

    public String getArchiveNameNoExt() {
        if (archiveName == null || archiveName.isEmpty()) {
            return archiveName;
        }

        int lastDotIndex = archiveName.lastIndexOf('.');
        return (lastDotIndex == -1) ? archiveName : archiveName.substring(0, lastDotIndex);
    }

    public String createCaseReference() {
        String urnTrimmed = urn != null ? urn.trim() : "";
        String exhibitTrimmed = exhibitReference != null ? exhibitReference.trim() : "";

        boolean urnValid = urnTrimmed.length() >= 9;
        boolean exhibitValid = exhibitTrimmed.length() >= 7;

        if (urnValid) {
            return urnTrimmed;
        } else if (exhibitValid) {
            return exhibitTrimmed;
        }

        return "";
    }

    @Override
    public LocalDateTime getCreateTimeAsLocalDateTime() {
        if (this.createTime == null) {
            return LocalDateTime.now();
        }

        long seconds = this.createTime.toEpochSecond(ZoneOffset.UTC);
        if (seconds == 0 || seconds == 3600) {
            return LocalDateTime.now();
        }

        return this.createTime;
    }

    private LocalDateTime resolveCreateTime(LocalDateTime input) {
        if (input == null) {
            return LocalDateTime.now();
        }

        long seconds = input.toEpochSecond(ZoneOffset.UTC);
        if (seconds == 0 || seconds == 3600) {
            return LocalDateTime.now();
        }

        return input;
    }

    @Override
    public String toString() {
        return "ExtractedMetadata {"
            + "courtReference='" + courtReference + '\''
            + ", urn='" + urn + '\''
            + ", exhibitReference='" + exhibitReference + '\''
            + ", defendantLastName='" + defendantLastName + '\''
            + ", witnessFirstName='" + witnessFirstName + '\''
            + ", recordingVersion='" + recordingVersion + '\''
            + ", recordingVersionNumber='" + recordingVersionNumber + '\''
            + ", fileExtension='" + fileExtension + '\''
            + ", createTime=" + createTime
            + ", duration=" + duration
            + ", fileName='" + fileName + '\''
            + ", fileSize='" + fileSize + '\''
            + '}';
    }
}
