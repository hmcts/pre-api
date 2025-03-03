package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorDTO;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorPropertiesDTO;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CheckForStreamingLocatorsAndLiveEventsTest {

    private CheckForStreamingLocatorsAndLiveEvents checkForStreamingLocatorsAndLiveEventsTask;

    private static SlackClient slackClient;
    private static MediaServiceBroker mediaServiceBroker;
    private static MediaKind mediaKind;

    @BeforeEach
    public void setUp() {
        slackClient = mock(SlackClient.class);
        mediaServiceBroker = mock(MediaServiceBroker.class);
        mediaKind = mock(MediaKind.class);

        checkForStreamingLocatorsAndLiveEventsTask = new CheckForStreamingLocatorsAndLiveEvents(mediaServiceBroker,
                slackClient);
    }

    @Test
    public void testNoActiveEventsOrStreamingLocators() {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaKind);
        checkForStreamingLocatorsAndLiveEventsTask.run();

        verify(mediaServiceBroker, times(2)).getEnabledMediaService();
        verify(mediaKind, times(1)).getLiveEvents();
        verify(mediaKind, times(1)).getStreamingLocators();
        verify(slackClient, times(1)).postSlackMessage("{\"text\":\":globe_with_meridians: "
                + "*Environment:* null\\n\\n:warning: *Active live events:*\\n\\n\\t:white_check_mark: "
                + "There were no active live events found\\n\\n:warning: "
                + "*Active streaming locators:*\\n\\n\\t:white_check_mark: "
                + "There were no active streaming locators found\\n\\n\"}");
    }

    @Test
    public void testOnlyActiveEvents() {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaKind);
        when(mediaKind.getLiveEvents()).thenReturn(List.of(new LiveEventDTO("123", "event-name",
                "description", "resourceState",
                "inputRtmp")));

        checkForStreamingLocatorsAndLiveEventsTask.run();

        verify(mediaServiceBroker, times(2)).getEnabledMediaService();
        verify(mediaKind, times(1)).getLiveEvents();
        verify(mediaKind, times(1)).getStreamingLocators();
        verify(slackClient, times(1)).postSlackMessage("{\"text\":\":globe_with_meridians: "
                + "*Environment:* null\\n\\n:warning: *Active live events:*\\n\\n\\t:siren:"
                + " <https://app.io.mediakind.com/project/null/liveEvents/event-name|event-name> "
                + ":siren:\\n\\n:warning: *Active streaming locators:*\\n\\n\\t:white_check_mark: "
                + "There were no active streaming locators found\\n\\n\"}");
    }

    @Test
    public void testOnlyActiveStreamingLocators() {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaKind);
        when(mediaKind.getStreamingLocators()).thenReturn(List.of(
                new StreamingLocatorDTO(
                        "test-name",
                        "event-name",
                        "id",
                        "policyName",
                        new Date(),
                        new Date(),
                        new StreamingLocatorPropertiesDTO(
                                "altMediaId",
                                "asset-name",
                                List.of(new StreamingLocatorPropertiesDTO.ContentKey("id",
                                        "1", "2",
                                        "3", "4")), "defaultPolicyName",
                                new Timestamp(System.currentTimeMillis() + 3600000),
                                List.of("filter1", "filter2"),
                                new Timestamp(System.currentTimeMillis()),
                                "locator-id",
                                "streaming-policy"
                        )
                )
        ));

        checkForStreamingLocatorsAndLiveEventsTask.run();

        verify(mediaServiceBroker, times(2)).getEnabledMediaService();
        verify(mediaKind, times(1)).getLiveEvents();
        verify(mediaKind, times(1)).getStreamingLocators();
        verify(slackClient, times(1)).postSlackMessage("{\"text\":\":globe_with_meridians:"
                + " *Environment:* null\\n\\n:warning: *Active live events:*\\n\\n\\t:white_check_mark: "
                + "There were no active live events found\\n\\n:warning: "
                + "*Active streaming locators:*\\n\\n\\t:siren: test-name :siren:\\n\\n\"}");
    }

    @Test
    public void testActiveStreamingLocatorsAndLiveEvents() {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaKind);
        when(mediaKind.getLiveEvents()).thenReturn(List.of(new LiveEventDTO("123", "event-name2",
                "description", "resourceState",
                "inputRtmp")));
        when(mediaKind.getStreamingLocators()).thenReturn(List.of(
                new StreamingLocatorDTO(
                        "test-name2",
                        "event-name",
                        "id",
                        "policyName",
                        new Date(),
                        new Date(),
                        new StreamingLocatorPropertiesDTO(
                                "altMediaId",
                                "asset-name",
                                List.of(new StreamingLocatorPropertiesDTO.ContentKey("id",
                                        "1", "2",
                                        "3", "4")), "defaultPolicyName",
                                new Timestamp(System.currentTimeMillis() + 3600000),
                                List.of("filter1", "filter2"),
                                new Timestamp(System.currentTimeMillis()),
                                "locator-id",
                                "streaming-policy"
                        )
                )
        ));

        checkForStreamingLocatorsAndLiveEventsTask.run();

        verify(mediaServiceBroker, times(2)).getEnabledMediaService();
        verify(mediaKind, times(1)).getLiveEvents();
        verify(mediaKind, times(1)).getStreamingLocators();
        verify(slackClient, times(1)).postSlackMessage("{\"text\":\":globe_with_meridians: "
                + "*Environment:* null\\n\\n:warning: *Active live events:*\\n\\n\\t:siren: "
                + "<https://app.io.mediakind.com/project/null/liveEvents/event-name2|event-name2>"
                + " :siren:\\n\\n:warning: *Active streaming locators:*\\n\\n\\t:siren: test-name2 :siren:\\n\\n\"}");

    }
}
