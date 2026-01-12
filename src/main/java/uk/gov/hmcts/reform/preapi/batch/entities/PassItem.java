package uk.gov.hmcts.reform.preapi.batch.entities;

public record PassItem(ExtractedMetadata item, ProcessedRecording cleansedData) {
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
