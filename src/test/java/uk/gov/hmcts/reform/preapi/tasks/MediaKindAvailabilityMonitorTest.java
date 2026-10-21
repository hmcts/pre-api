package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.media.MediaKindClient;
import uk.gov.hmcts.reform.preapi.media.MediaKindManagementClient;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaKindAvailabilityMonitorTest {

    private MediaKindManagementClient mediaKindManagementClient;
    private MediaKindClient mediaKindClient;
    private MediaKindAvailabilityMonitor monitor;

    @BeforeEach
    void setUp() {
        mediaKindManagementClient = mock(MediaKindManagementClient.class);
        mediaKindClient = mock(MediaKindClient.class);
        monitor = new MediaKindAvailabilityMonitor(mediaKindManagementClient, mediaKindClient);
    }

    @Test
    void shouldTreatReadyAsSuccessWhenNoExceptionIsThrown() {
        doNothing().when(mediaKindManagementClient).getReady();

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, never()).getProfile();
        verify(mediaKindClient, times(1)).getAssets(0, 10);
    }

    @Test
    void shouldTriggerProfileFallbackWhenReadyThrowsException() {
        doThrow(new RuntimeException("ready failed")).when(mediaKindManagementClient).getReady();
        when(mediaKindManagementClient.getProfile()).thenReturn(new Object());

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, times(1)).getProfile();
        verify(mediaKindClient, times(1)).getAssets(0, 10);
    }

    @Test
    void shouldNotCrashWhenBothReadyAndProfileThrowExceptions() {
        doThrow(new RuntimeException("ready failed")).when(mediaKindManagementClient).getReady();
        when(mediaKindManagementClient.getProfile()).thenThrow(new RuntimeException("profile failed"));

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, times(1)).getProfile();
        verify(mediaKindClient, times(1)).getAssets(0, 10);
    }

    @Test
    void shouldNotCrashWhenAssetsCheckThrowsException() {
        doNothing().when(mediaKindManagementClient).getReady();
        when(mediaKindClient.getAssets(0, 10)).thenThrow(new RuntimeException("assets failed"));

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, never()).getProfile();
        verify(mediaKindClient, times(1)).getAssets(0, 10);
    }

    @Test
    void shouldRunAssetsCheckEvenWhenReadyAndProfileFail() {
        doThrow(new RuntimeException("ready failed")).when(mediaKindManagementClient).getReady();
        when(mediaKindManagementClient.getProfile()).thenThrow(new RuntimeException("profile failed"));

        assertDoesNotThrow(() -> monitor.checkAvailability());

        verify(mediaKindManagementClient, times(1)).getReady();
        verify(mediaKindManagementClient, times(1)).getProfile();
        verify(mediaKindClient, times(1)).getAssets(0, 10);
    }

    @Test
    void shouldClassifyTimeoutException() throws Exception {
        String classification = invokeClassify(new SocketTimeoutException("timed out"));
        assertEquals("timeout", classification);
    }

    @Test
    void shouldClassifyConnectionException() throws Exception {
        String classification = invokeClassify(new UnknownHostException("cannot resolve host"));
        assertEquals("connection", classification);
    }

    @Test
    void shouldClassifyFeignExceptionAsHttpError() throws Exception {
        Exception feignLikeException = new FeignClientException("bad gateway");

        String classification = invokeClassify(feignLikeException);

        assertEquals("http_error", classification);
    }

    @Test
    void shouldClassifyUnexpectedException() throws Exception {
        String classification = invokeClassify(new IllegalStateException("boom"));
        assertEquals("unexpected_error", classification);
    }

    @Test
    void shouldReturnDefaultMessageWhenExceptionMessageIsNull() throws Exception {
        Exception ex = new RuntimeException((String) null);

        String message = invokeExceptionMessage(ex);

        assertEquals("<no exception message>", message);
    }

    @Test
    void shouldReturnExceptionMessageWhenPresent() throws Exception {
        Exception ex = new RuntimeException("something went wrong");

        String message = invokeExceptionMessage(ex);

        assertEquals("something went wrong", message);
    }

    private String invokeClassify(Exception ex) throws Exception {
        Method method = MediaKindAvailabilityMonitor.class.getDeclaredMethod("classify", Exception.class);
        method.setAccessible(true);
        return (String) method.invoke(monitor, ex);
    }

    private String invokeExceptionMessage(Exception ex) throws Exception {
        Method method = MediaKindAvailabilityMonitor.class.getDeclaredMethod("exceptionMessage", Exception.class);
        method.setAccessible(true);
        return (String) method.invoke(monitor, ex);
    }

    static class FeignClientException extends RuntimeException {
        FeignClientException(String message) {
            super(message);
        }
    }
}
