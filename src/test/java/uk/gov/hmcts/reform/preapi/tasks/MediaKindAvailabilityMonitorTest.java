package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import uk.gov.hmcts.reform.preapi.media.MediaKindClient;
import uk.gov.hmcts.reform.preapi.media.MediaKindManagementClient;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

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

    private static final UUID TEST_COURT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECORDING_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000002");

    private MediaKindManagementClient mediaKindManagementClient;
    private MediaKindClient mediaKindClient;
    private RecordingRepository recordingRepository;
    private MediaKindAvailabilityMonitor monitor;

    @BeforeEach
    void setUp() {
        mediaKindManagementClient = mock(MediaKindManagementClient.class);
        mediaKindClient = mock(MediaKindClient.class);
        recordingRepository = mock(RecordingRepository.class);

        when(recordingRepository.findRecordingIdsByCourtId(
            TEST_COURT_ID,
            PageRequest.of(0, 1)
        )).thenReturn(List.of(RECORDING_ID));

        monitor = new MediaKindAvailabilityMonitor(
            mediaKindManagementClient,
            mediaKindClient,
            recordingRepository,
            TEST_COURT_ID
        );
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

    @Test
    void shouldCheckReadinessAssetsAndVod() {
        monitor.checkAvailability();

        verify(mediaKindManagementClient).getReady();
        verify(mediaKindManagementClient, never()).getProfile();
        verify(mediaKindClient).getAssets(0, 10);
        verify(recordingRepository).findRecordingIdsByCourtId(
            TEST_COURT_ID,
            PageRequest.of(0, 1)
        );
        verify(mediaKindClient).getAsset(RECORDING_ID.toString());
    }

    @Test
    void shouldUseProfileWhenReadinessFails() {
        doThrow(new RuntimeException("readiness unavailable"))
            .when(mediaKindManagementClient).getReady();

        monitor.checkAvailability();

        verify(mediaKindManagementClient).getProfile();
        verify(mediaKindClient).getAssets(0, 10);
    }

    @Test
    void shouldContinueWhenReadinessAndProfileFail() {
        doThrow(new RuntimeException("readiness unavailable"))
            .when(mediaKindManagementClient).getReady();
        doThrow(new RuntimeException("profile unavailable"))
            .when(mediaKindManagementClient).getProfile();

        assertDoesNotThrow(monitor::checkAvailability);

        verify(mediaKindClient).getAssets(0, 10);
    }

    @Test
    void shouldContainAssetsFailure() {
        doThrow(new RuntimeException("assets unavailable"))
            .when(mediaKindClient).getAssets(0, 10);

        assertDoesNotThrow(monitor::checkAvailability);
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
