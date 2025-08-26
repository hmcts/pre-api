package uk.gov.hmcts.reform.preapi.alerts;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@FeignClient(name = "slackClient", url = "${slack.webhook}")
public interface SlackClient {

    @PostMapping
    void postSlackMessage(@RequestBody String slackMessage);
}
