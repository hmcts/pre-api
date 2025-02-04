package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Represents cleansed data for a recording.
 */
public class CleansedData {

    private String courtReference;
    private String fullCourtName;
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
    private boolean isTest;
    private boolean isMostRecentVersion;
    private String fileExtension;
    private String fileName;
    private TestItem testCheckResult;
    private List<Map<String, String>> shareBookingContacts;
    
    
    private CleansedData(Builder builder) {
        this.courtReference = builder.courtReference;
        this.fullCourtName = builder.fullCourtName;
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
        this.isTest = builder.isTest;
        this.isMostRecentVersion = builder.isMostRecentVersion;
        this.fileExtension = builder.fileExtension;
        this.fileName = builder.fileName;
        this.testCheckResult = builder.testCheckResult;
        this.shareBookingContacts = builder.shareBookingContacts;
    }

    // Getters
    public String getCourtReference() { 
        return courtReference; 
    }

    public String getFullCourtName() { 
        return fullCourtName; 
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
        return "ORIG".equalsIgnoreCase(this.getRecordingVersion()); 
    }

    public boolean isCopy() { 
        return "COPY".equalsIgnoreCase(this.getRecordingVersion()); 
    }

    public Duration getDuration() { 
        return duration; 
    }

    public boolean isTest() { 
        return isTest; 
    }

    public boolean isMostRecentVersion() { 
        return isMostRecentVersion; 
    }

    public TestItem getTestCheckResult() { 
        return testCheckResult; 
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

    @Override
    public String toString() {
        return new StringJoiner(", ", CleansedData.class.getSimpleName() + "{", "}")
            .add("courtReference='" + courtReference + "'")
            .add("fullCourtName='" + fullCourtName + "'")
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
            .add("isTest=" + isTest)
            .add("isMostRecentVersion=" + isMostRecentVersion)
            .add("fileExtension='" + fileExtension + "'")
            .add("fileName='" + fileName + "'")
            .add("testCheckResult=" + testCheckResult)
            .add("shareBookingContacts=" + shareBookingContacts)
            .toString();
    }

    /**
     * Builder for creating instances of CleansedData.
     */
    public static class Builder {
        private String courtReference;
        private String fullCourtName;
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
        private boolean isTest;
        private boolean isMostRecentVersion;
        private String fileExtension;
        private String fileName;
        private TestItem testCheckResult;
        private List<Map<String, String>> shareBookingContacts;

        public Builder setCourtReference(String courtReference) {
            this.courtReference = courtReference;
            return this;
        }

        public Builder setFullCourtName(String fullCourtName) {
            this.fullCourtName = fullCourtName;
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

        public Builder setIsTest(boolean isTest) {
            this.isTest = isTest;
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

        public Builder setTestCheckResult(TestItem testCheckResult) {
            this.testCheckResult = testCheckResult;
            return this;
        }

        public Builder setShareBookingContacts(List<Map<String, String>> shareBookingContacts) {
            this.shareBookingContacts = shareBookingContacts;
            return this;
        }

        public CleansedData build() {
            return new CleansedData(this);
        }
    }
}

