package uk.gov.hmcts.reform.preapi.email;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.hmcts.reform.preapi.config.FlowHttpClientConfiguration;
import uk.gov.hmcts.reform.preapi.dto.StoppedLiveEventsNotificationDTO;

import java.util.List;

@FeignClient(name = "flowHttpClient", url = "${flow.api}", configuration = FlowHttpClientConfiguration.class)
public interface FlowHttpClient {

    @PostMapping("/workflows/${flow.workflow}/triggers/manual/paths/invoke")
    void emailAfterStoppingLiveEvents(
        @RequestBody List<StoppedLiveEventsNotificationDTO> stoppedLiveEventsNotification
    );
}
