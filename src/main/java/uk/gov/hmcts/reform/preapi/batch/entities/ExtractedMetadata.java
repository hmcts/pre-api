package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public class ExtractedMetadata {
    private final String courtReference;
    private final String urn;
    private final String exhibitReference;
    private final String defendantLastName;
    private final String witnessFirstName;
    private final String recordingVersion;
    private final String recordingVersionNumber;
    private final String fileExtension;
    private final LocalDateTime createTime;
    private final int duration;
    private final String fileName;
    private final String fileSize;
    private final String archiveName;
    private final String sanitizedArchiveName = "";

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
        String archiveName
    ) {
        this.courtReference = courtReference;
        this.urn = urn;
        this.exhibitReference = exhibitReference;
        this.defendantLastName = formatName(defendantLastName.toLowerCase());
        this.witnessFirstName = formatName(witnessFirstName.toLowerCase());
        this.recordingVersion = recordingVersion;
        this.recordingVersionNumber = recordingVersionNumber;
        this.fileExtension = fileExtension;
        this.createTime = createTime;
        this.duration = duration;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.archiveName = archiveName;
    }

    private String formatName(String name) {
        if (name == null) {
            return null;
        }

        return Arrays.stream(name.split("-"))
            .map(n -> StringUtils.capitalize(n.toLowerCase()))
            .collect(Collectors.joining("-"));
    }

    public String getArchiveNameNoExt() {
        if (archiveName == null || archiveName.isEmpty()) {
            return archiveName;
        }

        int lastDotIndex = archiveName.lastIndexOf('.');
        return (lastDotIndex == -1) ? archiveName : archiveName.substring(0, lastDotIndex);
    }

    // TODO remove unused ?
    // private String computeSanitizedName(String archiveName) {
    //     if (archiveName == null || archiveName.isEmpty()) {
    //         return "";
    //     }

    //     String sanitized = archiveName
    //         .replaceAll("^QC[_\\d]?", "")
    //         .replaceAll("^QC(?![A-Za-z])", "")
    //         .replaceAll("[-_\\s]QC\\d*(?=\\.[a-zA-Z0-9]+$|$)", "")
    //         .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
    //         .replaceAll("_(?=\\.[^.]+$)", "")
    //         .replaceAll("[-_\\s]{2,}", "-")
    //         .trim();

    //     return sanitized;
    // }

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
