package uk.gov.hmcts.reform.preapi.email;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.hmcts.reform.preapi.config.FlowHttpClientConfiguration;
import uk.gov.hmcts.reform.preapi.dto.flow.CaseStateChangeNotificationDTO;
import uk.gov.hmcts.reform.preapi.dto.flow.StoppedLiveEventsNotificationDTO;

import java.util.List;

@FeignClient(name = "flowHttpClient", url = "${flow.api}", configuration = FlowHttpClientConfiguration.class)
public interface FlowHttpClient {

    @PostMapping("/workflows/${flow.workflow.stopLiveEventNotifier}/triggers/manual/paths/invoke")
    void emailAfterStoppingLiveEvents(
        @RequestBody List<StoppedLiveEventsNotificationDTO> stoppedLiveEventsNotification
    );

    @PostMapping("/workflows/${flow.workflow.caseStateChangeNotifier}/triggers/manual/paths/invoke")
    void emailAfterCaseStateChange(@RequestBody List<CaseStateChangeNotificationDTO> caseStateChangeNotification);
}
