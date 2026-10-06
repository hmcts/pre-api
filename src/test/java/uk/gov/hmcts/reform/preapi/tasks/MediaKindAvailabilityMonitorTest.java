package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.media.MediaKindManagementClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaKindAvailabilityMonitorTest {

    private MediaKindManagementClient mediaKindManagementClient;
    private MediaKindAvailabilityMonitor monitor;

    @BeforeEach
    void setUp() {
        mediaKindManagementClient = mock(MediaKindManagementClient.class);
        monitor = new MediaKindAvailabilityMonitor(mediaKindManagementClient);
    }

    @Test
    void shouldTreatReadyAsSuccessWhenNoExceptionIsThrown() {
        doNothing().when(mediaKindManagementClient).getReady();

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, never()).getProfile();
    }

    @Test
    void shouldTriggerProfileFallbackWhenReadyThrowsException() {
        doThrow(new RuntimeException("ready failed")).when(mediaKindManagementClient).getReady();
        when(mediaKindManagementClient.getProfile()).thenReturn(new Object());

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, times(1)).getProfile();
    }

    @Test
    void shouldNotCrashWhenBothReadyAndProfileThrowExceptions() {
        doThrow(new RuntimeException("ready failed")).when(mediaKindManagementClient).getReady();
        when(mediaKindManagementClient.getProfile()).thenThrow(new RuntimeException("profile failed"));

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, times(1)).getProfile();
    }
}
