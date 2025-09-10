package uk.gov.hmcts.reform.preapi.alerts;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Connects to PRE Alerts app.
 * Posts to Reform #dts-pre-rec-evidence-tech https://moj.enterprise.slack.com/archives/C02F3N771U7.
 */
@FeignClient(name = "slackClient", url = "${slack.webhook}")
public interface SlackClient {

    @PostMapping
    void postSlackMessage(@RequestBody String slackMessage);
}
