package uk.gov.hmcts.reform.preapi.entities.batch;

import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;

public class CleansedData {

    private String courtReference;
    private String fullCourtName;
    private Timestamp recordingTimestamp;
    private String urn;
    private String exhibitReference;
    private String defendantLastName;
    private String witnessFirstName;
    private String recordingVersion;
    private int recordingVersionNumber;
    private Duration duration;
    private boolean isTest;
    private TestItem testCheckResult;
    private Court court;
    private CaseState state;

    public CleansedData() {
    }

    public CleansedData(String courtReference, 
                        String fullCourtName, 
                        Timestamp recordingTimestamp,
                        String urn, 
                        String exhibitReference, 
                        String defendantLastName, 
                        String witnessFirstName, 
                        String recordingVersion, 
                        int recordingVersionNumber,
                        Duration duration,
                        boolean isTest, 
                        TestItem testCheckResult, 
                        Court court,
                        CaseState state
    ) {
        this.courtReference = courtReference;
        this.fullCourtName = fullCourtName;
        this.recordingTimestamp = recordingTimestamp;
        this.urn = urn;
        this.exhibitReference = exhibitReference;
        this.defendantLastName = defendantLastName;
        this.witnessFirstName = witnessFirstName;
        this.recordingVersion = recordingVersion;
        this.recordingVersionNumber = recordingVersionNumber;
        this.duration = duration;
        this.isTest = isTest;
        this.testCheckResult = testCheckResult;
        this.court = court;
        this.state = state;
    }

    public String getCourtReference() { 
        return courtReference; 
    }

    public void setCourtReference(String courtReference) { 
        this.courtReference = courtReference; 
    }

    public String getFullCourtName() { 
        return fullCourtName; 
    }

    public void setFullCourtName(String fullCourtName) { 
        this.fullCourtName = fullCourtName; 
    }

    public Timestamp getRecordingTimestamp() { 
        return recordingTimestamp; 
    }

    public void setRecordingTimestamp(Timestamp recordingTimestamp) { 
        this.recordingTimestamp = recordingTimestamp; 
    }

    public String getUrn() { 
        return urn; 
    }

    public void setUrn(String urn) { 
        this.urn = urn; 
    }

    public String getExhibitReference() { 
        return exhibitReference; 
    }

    public void setExhibitReference(String exhibitReference) { 
        this.exhibitReference = exhibitReference; 
    }

    public String getDefendantLastName() { 
        return defendantLastName; 
    }

    public void setDefendantLastName(String defendantLastName) { 
        this.defendantLastName = defendantLastName; 
    }

    public String getWitnessFirstName() { 
        return witnessFirstName; 
    }

    public void setWitnessFirstName(String witnessFirstName) { 
        this.witnessFirstName = witnessFirstName; 
    }

    public String getRecordingVersion() { 
        return recordingVersion; 
    }

    public void setRecordingVersion(String recordingVersion) { 
        this.recordingVersion = recordingVersion; 
    }

    public int getRecordingVersionNumber() { 
        return recordingVersionNumber; 
    }

    public void setRecordingVersionNumber(int recordingVersionNumber) { 
        this.recordingVersionNumber = recordingVersionNumber; 
    }

    public Duration getDuration() { 
        return duration; 
    }

    public void setDuration(Duration duration) { 
        this.duration = duration;
    }

    public boolean isTest() { 
        return isTest; 
    }

    public void setTest(boolean test) { 
        isTest = test; 
    }

    public TestItem getTestCheckResult() {
        return testCheckResult;
    }

    public void setTestCheckResult(TestItem testCheckResult) {
        this.testCheckResult = testCheckResult;
    }

    public Court getCourt() { 
        return court; 
    }

    public void setCourt(Court court) { 
        this.court = court; 
    }

    public CaseState getState() { 
        return state; 
    }

    public void setState(CaseState state) { 
        this.state = state; 
    }

    // @Override
    // public String toString() {
    //     return "CleansedData{" 
    //             + "courtReference='" + courtReference + '\'' 
    //             + ", fullCourtName='" + fullCourtName + '\'' 
    //             + ", recordingTimestamp='" + recordingTimestamp + '\'' 
    //             + ", urn='" + urn + '\'' 
    //             + ", exhibitReference='" + exhibitReference + '\'' 
    //             + ", defendantLastName='" + defendantLastName + '\'' 
    //             + ", witnessFirstName='" + witnessFirstName + '\'' 
    //             + ", recordingVersion='" + recordingVersion + '\'' 
    //             + ", duration='" + duration + '\'' 
    //             + ", isTest=" + isTest 
    //             + ", courtId=" + court 
    //             + '}';
    // }

    @Override
    public String toString() {
        return "CleansedData{" 
                + "courtReference='" + (courtReference != null ? courtReference : "null")
                + ", fullCourtName='" + (fullCourtName != null ? fullCourtName : "null")
                + ", recordingTimestamp=" + (recordingTimestamp != null ? recordingTimestamp : "null") 
                + ", urn='" + (urn != null ? urn : "null")
                + ", exhibitReference='" + (exhibitReference != null ? exhibitReference : "null")
                + ", defendantLastName='" + (defendantLastName != null ? defendantLastName : "null")
                + ", witnessFirstName='" + (witnessFirstName != null ? witnessFirstName : "null")
                + ", recordingVersion='" + (recordingVersion != null ? recordingVersion : "null")
                + ", recordingVersionNumber=" + recordingVersionNumber 
                + ", duration=" + (duration != null ? duration : "null") 
                + ", isTest=" + isTest 
                + ", testCheckResult=" + (testCheckResult != null ? testCheckResult : "null") 
                + ", court=" + (court != null ? court : "null") 
                + ", state=" + (state != null ? state : "null") 
                + '}';
    }

}
