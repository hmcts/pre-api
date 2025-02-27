package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class CheckForMissingRecordingsTest {

    private CheckForMissingRecordings checkForMissingRecordingsTask;
    private CaptureSessionService captureSessionService;
    private SlackClient slackClient;

    @BeforeEach
    public void setUp() {
        String platformEnv = "Local-Testing";

        slackClient = mock(SlackClient.class);
        captureSessionService = mock(CaptureSessionService.class);
        when(captureSessionService.findMissingRecordingIds(any())).thenReturn(Arrays.asList("123", "456"));

        checkForMissingRecordingsTask = new CheckForMissingRecordings(captureSessionService, slackClient, platformEnv);
    }

    @Test
    public void testMissingRecordingsAreFound() {
        when(captureSessionService.findMissingRecordingIds(any())).thenReturn(Arrays.asList("123", "456"));
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findMissingRecordingIds(any());
        verify(slackClient, times(1)).postSlackMessage("{\"text\":\":globe_with_meridians: *Environment:* Local-Testing\\n\\n" +
                                                           ":warning: *Missing Recordings:*" +
                                                           "\\n\\n\\t:siren: 123 :siren:" +
                                                           "\\n\\t:siren: 456 :siren:\\n\\n\"}");
    }

    @Test
    public void testMissingRecordingsAreNotFound() {
        when(captureSessionService.findMissingRecordingIds(any())).thenReturn(Collections.emptyList());
        checkForMissingRecordingsTask.run();

        verify(captureSessionService, times(1)).findMissingRecordingIds(any());
        verify(slackClient, times(0)).postSlackMessage(anyString());
    }

}
