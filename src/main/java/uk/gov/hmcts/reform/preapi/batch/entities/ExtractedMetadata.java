package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class ExtractedMetadata implements IArchiveData {
    private String courtReference;
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

    public ExtractedMetadata(
        String courtReference,
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
        String archiveName
    ) {
        this.courtReference = courtReference;
        this.urn = urn != null ? urn.toUpperCase() : null;
        this.exhibitReference = exhibitReference != null ? exhibitReference.toUpperCase() : null;
        this.defendantLastName = formatName(
            defendantLastName != null ? defendantLastName.toLowerCase() : ""
        );
        this.witnessFirstName = formatName(
            witnessFirstName != null ? witnessFirstName.toLowerCase() : ""
        );
        this.recordingVersion = recordingVersion;
        this.recordingVersionNumber = recordingVersionNumber;
        this.fileExtension = fileExtension;
        this.createTime = createTime;
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
                    return StringUtils.capitalize(part.toLowerCase());
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

    public String getSanitizedArchiveName() {
        return sanitizedArchiveName;
    }

    public String createCaseReference() {
        if ((urn == null || urn.isEmpty()) && (exhibitReference == null || exhibitReference.isEmpty())) {
            return "";
        }

        if (urn == null || urn.isEmpty()) {
            return exhibitReference;
        }

        if (exhibitReference == null || exhibitReference.isEmpty()) {
            return urn;
        }

        return urn + "-" + exhibitReference;
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

    public boolean isCreateTimeDefaulted() {
        if (this.createTime == null) {
            return true;
        }
        
        long seconds = this.createTime.toEpochSecond(ZoneOffset.UTC);
        return seconds == 0 || seconds == 3600;
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
