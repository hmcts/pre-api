package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;

@AllArgsConstructor
public class PassItem {
    private final ExtractedMetadata item;
    private final ProcessedRecording cleansedData;

    public String getArchiveName() {
        return item.getArchiveName();
    }

    public String getCaseReference() {
        return cleansedData.getCaseReference();
    }

    public String getWitnessName() {
        return cleansedData.getWitnessFirstName();
    }

    public String getDefendantName() {
        return cleansedData.getDefendantLastName();
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
        return item.getFileName();
    }

    public Duration getDuration() {
        return cleansedData.getDuration();
    }

    public String getFileSize() {
        return item.getFileSize();
    }


    @Override
    public String toString() {
        return "PassItem{"
            + ", archiveName='" + item.getArchiveName()
            + ", caseReference='" + cleansedData.getCaseReference()
            + ", witness='" + cleansedData.getWitnessFirstName()
            + ", defendant='" + cleansedData.getDefendantLastName()
            + ", scheduledFor=" + cleansedData.getRecordingTimestamp()
            + ", state='" + cleansedData.getState()
            + ", version=" + cleansedData.getRecordingVersionNumber()
            + ", fileName='" + item.getFileName()
            + ", duration=" + cleansedData.getDuration()
            + '}';
    }
}
