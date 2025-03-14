package uk.gov.hmcts.reform.preapi.batch.entities;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

public class ExtractedMetadata {
    private String courtReference;
    private String date;
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
    private String sanitizedName;

    public ExtractedMetadata(String courtReference, String date, String urn, String exhibitReference,
                             String defendantLastName, String witnessFirstName, String recordingVersion,
                             String recordingVersionNumber, String fileExtension, LocalDateTime createTime,
                             int duration, String fileName, String fileSize, String sanitizedName) {
        this.courtReference = courtReference;
        this.date = date;
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
        this.sanitizedName = sanitizedName;
    }

    public String getCourtReference() { 
        return courtReference; 
    }

    public String getDate() { 
        return date; 
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

    public String getSanitizedName() { 
        return sanitizedName; 
    } 

    private String formatName(String name) {
        return name != null ? StringUtils.capitalize(name.toLowerCase()) : null;
    }

    public String createCaseReference() {
        StringBuilder referenceBuilder = new StringBuilder();
        if (urn != null && !urn.isEmpty()) {
            referenceBuilder.append(urn);
        }
        if (exhibitReference != null && !exhibitReference.isEmpty()) {
            if (referenceBuilder.length() > 0) {
                referenceBuilder.append("-");
            }
            referenceBuilder.append(exhibitReference);
        }
        return referenceBuilder.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ExtractedMetadata {");
        sb.append("courtReference='").append(courtReference).append('\'');
        sb.append(", date='").append(date).append('\'');
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
