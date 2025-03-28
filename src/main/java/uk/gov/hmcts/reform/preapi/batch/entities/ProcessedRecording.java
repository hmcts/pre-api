package uk.gov.hmcts.reform.preapi.batch.entities;

import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class ProcessedRecording {
    private String courtReference;
    private String courtName;
    private Court court;
    private CaseState state;
    private Timestamp recordingTimestamp;
    private String urn;
    private String exhibitReference;
    private String caseReference;
    private String defendantLastName;
    private String witnessFirstName;
    private String recordingVersion;
    private String recordingVersionNumberStr;
    private int recordingVersionNumber;
    private Duration duration;
    private boolean isMostRecentVersion;
    private String fileExtension;
    private String fileName;
    private List<Map<String, String>> shareBookingContacts;
    
    
    private ProcessedRecording(Builder builder) {
        this.courtReference = builder.courtReference;
        this.courtName = builder.courtName;
        this.court = builder.court;
        this.state = builder.state;
        this.recordingTimestamp = builder.recordingTimestamp;
        this.urn = builder.urn;
        this.exhibitReference = builder.exhibitReference;
        this.defendantLastName = builder.defendantLastName;
        this.witnessFirstName = builder.witnessFirstName;
        this.recordingVersion = builder.recordingVersion;
        this.caseReference = builder.caseReference;
        this.recordingVersionNumberStr = builder.recordingVersionNumberStr;
        this.recordingVersionNumber = builder.recordingVersionNumber;
        this.duration = builder.duration;
        this.isMostRecentVersion = builder.isMostRecentVersion;
        this.fileExtension = builder.fileExtension;
        this.fileName = builder.fileName;
        this.shareBookingContacts = builder.shareBookingContacts;
    }

    public String getCourtReference() { 
        return courtReference; 
    }

    public String getFullCourtName() { 
        return courtName; 
    }

    public Timestamp getRecordingTimestamp() { 
        return recordingTimestamp; 
    }

    public String getUrn() { 
        return urn; 
    }

    public String getCaseReference() { 
        return caseReference; 
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

    public String getRecordingVersionNumberStr() { 
        return recordingVersionNumberStr; 
    }
    
    public int getRecordingVersionNumber() { 
        return recordingVersionNumber; 
    }

    public boolean isOrig() { 
        return Constants.VALID_ORIG_TYPES.contains(this.getRecordingVersion().toUpperCase());
    }

    public boolean isCopy() { 
        return Constants.VALID_COPY_TYPES.contains(this.getRecordingVersion().toUpperCase());
    }

    public Duration getDuration() { 
        return duration; 
    }

    public boolean isMostRecentVersion() { 
        return isMostRecentVersion; 
    }

    public String getFileExtension() { 
        return fileExtension; 
    }

    public String getFileName() { 
        return fileName; 
    }

    public Court getCourt() { 
        return court; 
    }

    public CaseState getState() { 
        return state; 
    }

    public boolean isRecordingType(String type) { 
        return type.equalsIgnoreCase(this.recordingVersion); 
    }

    public List<Map<String, String>> getShareBookingContacts() { 
        return shareBookingContacts; 
    }

    public Timestamp getFinishedAt() {
        if (recordingTimestamp == null || duration == null) {
            return null;
        }
        Instant finishedAt = recordingTimestamp.toInstant().plus(duration);
        return Timestamp.from(finishedAt);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ProcessedRecording.class.getSimpleName() + "{", "}")
            .add("courtReference='" + courtReference + "'")
            .add("courtName='" + courtName + "'")
            .add("court=" + court)
            .add("state=" + state)
            .add("recordingTimestamp=" + recordingTimestamp)
            .add("urn='" + urn + "'")
            .add("exhibitReference='" + exhibitReference + "'")
            .add("defendantLastName='" + defendantLastName + "'")
            .add("witnessFirstName='" + witnessFirstName + "'")
            .add("recordingVersion='" + recordingVersion + "'")
            .add("caseReference='" + caseReference + "'")
            .add("recordingVersionNumberStr=" + recordingVersionNumberStr)
            .add("recordingVersionNumber=" + recordingVersionNumber)
            .add("duration=" + duration)
            .add("isMostRecentVersion=" + isMostRecentVersion)
            .add("fileExtension='" + fileExtension + "'")
            .add("fileName='" + fileName + "'")
            .add("shareBookingContacts=" + shareBookingContacts)
            .toString();
    }


    public static class Builder {
        private String courtReference;
        private String courtName;
        private Court court;
        private CaseState state;
        private Timestamp recordingTimestamp;
        private String urn;
        private String exhibitReference;
        private String defendantLastName;
        private String witnessFirstName;
        private String recordingVersion;
        private String caseReference;
        private String recordingVersionNumberStr;
        private int recordingVersionNumber;
        private Duration duration;
        private boolean isMostRecentVersion;
        private String fileExtension;
        private String fileName;
        private List<Map<String, String>> shareBookingContacts;

        public Builder setCourtReference(String courtReference) {
            this.courtReference = courtReference;
            return this;
        }

        public Builder setFullCourtName(String courtName) {
            this.courtName = courtName;
            return this;
        }

        public Builder setCourt(Court court) {
            this.court = court;
            return this;
        }

        public Builder setState(CaseState state) {
            this.state = state;
            return this;
        }

        public Builder setRecordingTimestamp(Timestamp recordingTimestamp) {
            this.recordingTimestamp = recordingTimestamp;
            return this;
        }

        public Builder setUrn(String urn) {
            this.urn = urn;
            return this;
        }

        public Builder setExhibitReference(String exhibitReference) {
            this.exhibitReference = exhibitReference;
            return this;
        }

        public Builder setDefendantLastName(String defendantLastName) {
            this.defendantLastName = defendantLastName;
            return this;
        }

        public Builder setWitnessFirstName(String witnessFirstName) {
            this.witnessFirstName = witnessFirstName;
            return this;
        }

        public Builder setRecordingVersion(String recordingVersion) {
            this.recordingVersion = recordingVersion;
            return this;
        }

        public Builder setCaseReference(String caseReference) {
            this.caseReference = caseReference;
            return this;
        }

        public Builder setRecordingVersionNumberStr(String recordingVersionNumberStr) {
            this.recordingVersionNumberStr = recordingVersionNumberStr;
            return this;
        }

        public Builder setRecordingVersionNumber(int recordingVersionNumber) {
            this.recordingVersionNumber = recordingVersionNumber;
            return this;
        }

        public Builder setDuration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder setIsMostRecentVersion(boolean isMostRecentVersion) {
            this.isMostRecentVersion = isMostRecentVersion;
            return this;
        }

        public Builder setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setShareBookingContacts(List<Map<String, String>> shareBookingContacts) {
            this.shareBookingContacts = shareBookingContacts;
            return this;
        }

        public ProcessedRecording build() {
            return new ProcessedRecording(this);
        }
    }
}

