package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessage;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageSection;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorDTO;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Check for any streaming locators/live events and send an alert if there are any.
 */
@Component
@Slf4j
public class CheckForStreamingLocatorsAndLiveEvents implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;
    private final SlackClient slackClient;

    @Value("${platform-env}")
    private String environment;

    @Value("${mediakind.subscription}")
    private String mediaKindSubscription;

    private final String mediaKindUrl = "https://app.io.mediakind.com/project";

    public CheckForStreamingLocatorsAndLiveEvents(MediaServiceBroker mediaServiceBroker, SlackClient slackClient) {
        this.mediaServiceBroker = mediaServiceBroker;
        this.slackClient = slackClient;
    }

    @Override
    public void run() {
        log.info("Running CheckForStreamingLocatorsAndLiveEvents task");

        List<LiveEventDTO> liveEventDTOList = mediaServiceBroker
                .getEnabledMediaService().getLiveEvents();

        List<StreamingLocatorDTO> streamingLocatorDTOList = mediaServiceBroker
                .getEnabledMediaService().getStreamingLocators();

        List<String> liveEvents = formatLiveEventUrls(liveEventDTOList);
        List<String> streamingLocators = streamingLocatorDTOList.stream()
                .map(StreamingLocatorDTO::getName)
                .collect(Collectors.toList());

        List<SlackMessageSection> sections = List.of(
                createSlackMessageSection("Active live events", liveEvents,
                        "There were no active live events found"),
                createSlackMessageSection("Active streaming locators", streamingLocators,
                        "There were no active streaming locators found")
        );

        slackClient.postSlackMessage(SlackMessage.builder()
                .environment(environment)
                .sections(sections)
                .build().toJson());

        log.info("Completed CheckForStreamingLocatorsAndLiveEvents task");
    }

    private List<String> formatLiveEventUrls(List<LiveEventDTO> liveEventDTOList) {
        return liveEventDTOList.stream()
                .map(event -> String.format("<%s/%s/liveEvents/%s|%s>",
                        mediaKindUrl, mediaKindSubscription, event.getName(), event.getName()))
                .collect(Collectors.toList());
    }

    private SlackMessageSection createSlackMessageSection(String title, List<String> items, String emptyMessage) {
        return new SlackMessageSection(title, items.isEmpty() ? List.of() : items, emptyMessage);
    }
}