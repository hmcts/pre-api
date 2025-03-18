package uk.gov.hmcts.reform.preapi.batch.entities;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

public class ExtractedMetadata {
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

    public String getCourtReference() { 
        return courtReference; 
    }

    public String getUrn() { 
        return urn; 
    }

    public String getExhibitReference() { 
        return exhibitReference; 
    }

    public String getDefendantLastName() { 
        return defendantLastName; 
    }

    public String getWitnessFirstName() { 
        return witnessFirstName; 
    }

    public String getRecordingVersion() { 
        return recordingVersion; 
    }

    public String getRecordingVersionNumber() { 
        return recordingVersionNumber; 
    }

    public String getFileExtension() { 
        return fileExtension; 
    }

    public LocalDateTime getCreateTime() { 
        return createTime; 
    }  

    public int getDuration() { 
        return duration; 
    }  

    public String getFileName() { 
        return fileName; 
    }  
    
    public String getFileSize() { 
        return fileSize; 
    } 

    private String formatName(String name) {
        return name != null ? StringUtils.capitalize(name.toLowerCase()) : null;
    }

    public String getArchiveName() {
        return archiveName;
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
        StringBuilder sb = new StringBuilder();
        sb.append("ExtractedMetadata {");
        sb.append("courtReference='").append(courtReference).append('\'');
        sb.append(", urn='").append(urn).append('\'');
        sb.append(", exhibitReference='").append(exhibitReference).append('\'');
        sb.append(", defendantLastName='").append(defendantLastName).append('\'');
        sb.append(", witnessFirstName='").append(witnessFirstName).append('\'');
        sb.append(", recordingVersion='").append(recordingVersion).append('\'');
        sb.append(", recordingVersionNumber='").append(recordingVersionNumber).append('\'');
        sb.append(", fileExtension='").append(fileExtension).append('\'');
        sb.append(", createTime=").append(createTime);
        sb.append(", duration=").append(duration);
        sb.append(", fileName='").append(fileName).append('\'');
        sb.append(", fileSize='").append(fileSize).append('\'');
        sb.append('}');
        return sb.toString();
    }

}
