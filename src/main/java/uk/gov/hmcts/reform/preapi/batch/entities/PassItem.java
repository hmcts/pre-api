package uk.gov.hmcts.reform.preapi.batch.entities;

import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;

public class PassItem {
    private String regexPattern;
    private CSVArchiveListData archiveItem;
    private CleansedData cleansedData;

    public PassItem(
        String regexPattern,
        CSVArchiveListData archiveItem, 
        CleansedData cleansedData
    ) {
        this.regexPattern = regexPattern;
        this.archiveItem = archiveItem;
        this.cleansedData = cleansedData;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public String getArchiveName() {
        return archiveItem.getArchiveName();
    }

    public String getCaseReference() {
        return cleansedData.getCaseReference();
    }

    public Timestamp getScheduledFor() {
        return cleansedData.getRecordingTimestamp();
    }

    public CaseState getState() {
        return cleansedData.getState();
    }

    public Integer getVersion() {
        return cleansedData.getRecordingVersionNumber();
    }

    public String getFileName() {
        return archiveItem.getFileName();
    }

    public Duration getDuration() {
        return cleansedData.getDuration();
    }

    public String getFileSize() {
        return archiveItem.getFileSize();
    }
  

    @Override
    public String toString() {
        return "PassItem{" 
                + "regexPattern='" + regexPattern 
                + ", archiveName='" + archiveItem.getArchiveName()  
                + ", caseReference='" + cleansedData.getCaseReference()
                + ", scheduledFor=" + cleansedData.getRecordingTimestamp() 
                + ", state='" + cleansedData.getState()  
                + ", version=" + cleansedData.getRecordingVersionNumber()
                + ", fileName='" + archiveItem.getFileName()  
                + ", duration=" + cleansedData.getDuration() 
            + '}';   
    }
}
