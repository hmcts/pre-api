package uk.gov.hmcts.reform.preapi.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest(classes = MediaKind.class)
public class MediaKindTest {
    private MediaKind mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaKind();
    }

    @DisplayName("Should throw Unsupported Operation Exception when method is not defined")
    @Test
    void unsupportedOperationException() {
        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.playAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.importAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.startLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.playLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.stopLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.getLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.getLiveEvents()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.getAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.getAssets()
        );
    }
}
