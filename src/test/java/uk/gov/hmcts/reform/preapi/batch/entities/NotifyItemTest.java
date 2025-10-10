package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyItemTest {

    @Test
    void testConstructorAndToString() {
        ProcessedRecording metadata = new ProcessedRecording();
        metadata.setUrn("URN123");
        metadata.setExhibitReference("EXH456");
        metadata.setFileName("test.mp4");
        metadata.setRecordingTimestamp(Timestamp.from(Instant.now()));
        metadata.setDuration(Duration.ofSeconds(100));
        metadata.setPreferred(true);


        NotifyItem notifyItem = new NotifyItem("Something went wrong", metadata);

        assertEquals("Something went wrong", notifyItem.getNotification());
        assertEquals(metadata, notifyItem.getExtractedMetadata());
        assertTrue(notifyItem.toString().contains("URN123"));
    }

    @Test
    void testNoArgsConstructor() {
        NotifyItem notifyItem = new NotifyItem();
        notifyItem.setNotification("Note");
        assertEquals("Note", notifyItem.getNotification());
    }
}