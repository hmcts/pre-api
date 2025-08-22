package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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

    private static final int MIN_LEN_EXCLUSIVE = 9;
    private static final int MAX_LEN_EXCLUSIVE = 20;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ExtractedMetadata(String courtReference,
                             UUID courtId,
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
                             String archiveName) {
        this.courtReference = courtReference;
        this.courtId = courtId;
        if (urn != null) {
            this.urn = urn.toUpperCase(Locale.UK);
        }
        if (exhibitReference != null) {
            this.exhibitReference = exhibitReference.toUpperCase(Locale.UK);
        }
        this.defendantLastName = formatName(defendantLastName != null ? defendantLastName.toLowerCase(Locale.UK) : "");
        this.witnessFirstName = formatName(witnessFirstName != null ? witnessFirstName.toLowerCase(Locale.UK) : "");
        this.recordingVersion = recordingVersion;
        this.recordingVersionNumber = recordingVersionNumber;
        this.fileExtension = fileExtension;
        this.createTime = resolveCreateTime(createTime);
        this.duration = duration;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.archiveId = archiveId;
        this.archiveName = archiveName;
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

    private static boolean isValidRef(String reference) {
        if (reference == null) {
            return false;
        }

        int len = reference.trim().length();
        return len >= MIN_LEN_EXCLUSIVE && len <= MAX_LEN_EXCLUSIVE;
    }

    public String createCaseReference() {
        if (isValidRef(urn)) {
            return urn.trim();
        }
        if (isValidRef(exhibitReference)) {
            return exhibitReference.trim();
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
