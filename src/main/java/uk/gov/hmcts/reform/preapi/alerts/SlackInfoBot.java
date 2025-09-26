package uk.gov.hmcts.reform.preapi.alerts;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Connects to Pre-Recorded Evidence (DTS) https://api.slack.com/apps/A09EA7KK6KX.
 * Posts to DTS #pre-incident-chat https://moj.enterprise.slack.com/archives/C091E43CGPQ.
 */
@FeignClient(name = "slackInfoBot", url = "${slack.infobot}")
public interface SlackInfoBot {

    @PostMapping
    void postSlackMessage(@RequestBody String slackMessage);
}
