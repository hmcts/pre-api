package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void getCreateTimeAsLocalDateTime_withTimestamp() {
        CSVExemptionListData data = new CSVExemptionListData();
        data.setCreateTime("1735689600000");

        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0),
                     data.getCreateTimeAsLocalDateTime());
    }

    @Test
    void getCreateTimeAsLocalDateTime_withDateString() {
        CSVExemptionListData data = new CSVExemptionListData();
        data.setCreateTime("01/01/2025 00:00");

        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0),
                     data.getCreateTimeAsLocalDateTime());
    }

    @Test
    void getCreateTimeAsLocalDateTime_withInvalidCreateTime() {
        CSVExemptionListData data = new CSVExemptionListData();
        data.setCreateTime("invalidDateTime");

        assertNull(data.getCreateTimeAsLocalDateTime());
    }

    @Test
    void getCreateTimeAsLocalDateTime_withNullCreateTime() {
        CSVExemptionListData data = new CSVExemptionListData();
        data.setCreateTime(null);

        assertNull(data.getCreateTimeAsLocalDateTime());
    }

    @Test
    void getCreateTimeAsLocalDateTime_withEmptyCreateTime() {
        CSVExemptionListData data = new CSVExemptionListData();
        data.setCreateTime("");

        assertNull(data.getCreateTimeAsLocalDateTime());
    }
}
