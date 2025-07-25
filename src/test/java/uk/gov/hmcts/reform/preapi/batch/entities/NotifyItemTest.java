package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyItemTest {

    @Test
    void testConstructorAndToString() {
        ExtractedMetadata metadata = new ExtractedMetadata(
            "CourtRef",
            UUID.randomUUID(),
            "urn123",
            "ex123",
            "doe",
            "john",
            "ORIG",
            "1",
            "mp4",
            LocalDateTime.now(),
            100,
            "file.mp4",
            "10MB",
            "archId123",
            "TestFile.mp4"
        );

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