package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PassItemTest {

    @Test
    void testPassItemToString() {
        ExtractedMetadata metadata = new ExtractedMetadata(
            "CourtRef",
            UUID.randomUUID(),
            "urn456",
            "ex456",
            "brown",
            "alex",
            "ORIG",
            "1",
            "mp4",
            LocalDateTime.now(),
            100,
            "test.mp4",
            "5MB",
            "arch456",
            "TestFile.mp4"
        );

        ProcessedRecording recording = ProcessedRecording.builder()
            .caseReference("CASE-123")
            .witnessFirstName("Alex")
            .defendantLastName("Brown")
            .recordingTimestamp(Timestamp.valueOf("2023-07-01 10:00:00"))
            .state(CaseState.OPEN)
            .recordingVersionNumber(2)
            .duration(Duration.ofSeconds(100))
            .build();
        PassItem item = new PassItem(metadata, recording);

        String output = item.toString();
        assertTrue(output.contains("archiveName='TestFile.mp4"));
        assertTrue(output.contains("caseReference='CASE-123"));
        assertTrue(output.contains("witness='Alex"));
        assertTrue(output.contains("defendant='Brown"));
        assertTrue(output.contains("version=2"));
    }
}