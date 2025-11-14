package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessedRecordingTest {
    @Test
    void shouldReturnCorrectFinishedAtWhenRecordingTimestampAndDurationAreProvided() {
        Instant recordingInstant = Instant.parse("2025-07-01T10:15:30.00Z");
        Timestamp recordingTimestamp = Timestamp.from(recordingInstant);
        Duration duration = Duration.ofMinutes(45);

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .recordingTimestamp(recordingTimestamp)
            .duration(duration)
            .build();

        Timestamp finishedAt = processedRecording.getFinishedAt();

        assertEquals(Timestamp.from(recordingInstant.plus(duration)), finishedAt);
    }

    @Test
    void shouldReturnNullWhenRecordingTimestampIsNull() {
        Duration duration = Duration.ofMinutes(45);

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .duration(duration)
            .build();

        Timestamp finishedAt = processedRecording.getFinishedAt();

        assertNull(finishedAt);
    }

    @Test
    void shouldReturnNullWhenDurationIsNull() {
        Instant recordingInstant = Instant.parse("2025-07-01T10:15:30.00Z");
        Timestamp recordingTimestamp = Timestamp.from(recordingInstant);

        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .recordingTimestamp(recordingTimestamp)
            .build();

        Timestamp finishedAt = processedRecording.getFinishedAt();

        assertNull(finishedAt);
    }

    @Test
    void shouldReturnNullWhenBothRecordingTimestampAndDurationAreNull() {
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .build();

        Timestamp finishedAt = processedRecording.getFinishedAt();

        assertNull(finishedAt);
    }
}
