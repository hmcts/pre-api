package uk.gov.hmcts.reform.preapi.email;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.hmcts.reform.preapi.config.CaseStateChangeFlowHttpClientConfiguration;
import uk.gov.hmcts.reform.preapi.dto.flow.CaseStateChangeNotificationDTO;

import java.util.List;

@FeignClient(
    name = "caseStateChangeNotifierFlowClient",
    url = "${flow.workflow.caseStateChangeNotifier.api}",
    configuration = CaseStateChangeFlowHttpClientConfiguration.class
)
public interface CaseStateChangeNotifierFlowClient {
    @PostMapping("/workflows/${flow.workflow.caseStateChangeNotifier.id}/triggers/manual/paths/invoke")
    void emailAfterCaseStateChange(@RequestBody List<CaseStateChangeNotificationDTO> caseStateChangeNotification);
}
