package uk.gov.hmcts.reform.preapi.config;

import org.springframework.beans.factory.annotation.Value;

public class CaseStateChangeFlowRequestInterceptor extends BaseFlowRequestInterceptor {
    @Value("${flow.workflow.caseStateChangeNotifier.sig:}")
    private String flowSig;

    @Value("${flow.workflow.caseStateChangeNotifier.id}")
    private String flowId;

    @Override
    protected String getFlowSig() {
        return flowSig;
    }

    @Override
    protected String getFlowId() {
        return flowId;
    }
}
