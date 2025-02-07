package uk.gov.hmcts.reform.preapi.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;

public class CaseStateChangeFlowRequestInterceptor implements RequestInterceptor {

    @Value("${flow.key}")
    private String flowKey;

    @Value("${flow.workflow.caseStateChangeNotifier.sig:}")
    private String flowSig;

    @Value("${flow.workflow.caseStateChangeNotifier.id}")
    private String flowId;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        if (!requestTemplate.path().contains(flowId)) {
            return;
        }

        requestTemplate.header("Content-Type", "application/json");
        requestTemplate.header("X-Flow-Key", flowKey);

        requestTemplate.query("api-version", "2016-06-01");
        requestTemplate.query("sp", "%2Ftriggers%2Fmanual%2Frun");
        requestTemplate.query("sv", "1.0");
        requestTemplate.query("sig", flowSig);
    }
}
