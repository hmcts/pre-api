package uk.gov.hmcts.reform.preapi.email;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.hmcts.reform.preapi.config.StopLiveEventFlowHttpClientConfiguration;
import uk.gov.hmcts.reform.preapi.dto.flow.StoppedLiveEventsNotificationDTO;

import java.util.List;

@FeignClient(
    name = "stopLiveEventNotifierFlowClient",
    url = "${flow.workflow.stopLiveEventNotifier.api}",
    configuration = StopLiveEventFlowHttpClientConfiguration.class
)
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface StopLiveEventNotifierFlowClient {

    @PostMapping("/workflows/${flow.workflow.stopLiveEventNotifier.id}/triggers/manual/paths/invoke")
    void emailAfterStoppingLiveEvents(
        @RequestBody List<StoppedLiveEventsNotificationDTO> stoppedLiveEventsNotification
    );
}
