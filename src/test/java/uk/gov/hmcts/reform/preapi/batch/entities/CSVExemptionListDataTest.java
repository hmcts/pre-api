package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CSVExemptionListDataTest {

    @Test
    void testToString() {
        CSVExemptionListData data = new CSVExemptionListData(
            "archiveId","archive1", "2023-10-01T10:00:00", 120, "courtRef123", "urn123",
            "exhibitRef123", "defendantName", "witnessName", "v1", 1,
            "mp4", "fileName.mp4", "100", "reason", "addedBy"
        );

        String expected = "CSVExemptionListData{"
            + "archiveId='archiveId'"
            + ", archiveName='archive1'"
            + ", createTime='2023-10-01T10:00:00'"
            + ", duration=120"
            + ", courtReference='courtRef123'"
            + ", urn='urn123'"
            + ", exhibitReference='exhibitRef123'"
            + ", defendantName='defendantName'"
            + ", witnessName='witnessName'"
            + ", recordingVersion='v1'"
            + ", recordingVersionNumber=1"
            + ", reason='reason'"
            + ", addedBy='addedBy'"
            + ", fileExtension='mp4'"
            + ", fileName='fileName.mp4'"
            + ", fileSize='100'"
            + '}';

        assertEquals(expected, data.toString());
    }
}
