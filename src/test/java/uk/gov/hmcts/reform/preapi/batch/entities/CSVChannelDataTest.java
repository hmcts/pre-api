package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CSVChannelDataTest {

    @Test
    void testToString() {
        CSVChannelData data = new CSVChannelData("Channel1", "User1", "user1@example.com", "Case123");
        String expected = "CSVChannelData{"
            + "channelName='Channel1'"
            + ", channelUser='User1'"
            + ", channelUserEmail='user1@example.com'"
            + ", caseReference='Case123'"
            + '}';
        assertEquals(expected, data.toString());
    }
}
