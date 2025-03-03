package uk.gov.hmcts.reform.preapi.config;

import org.springframework.beans.factory.annotation.Value;

public class StopLiveEventFlowRequestInterceptor extends BaseFlowRequestInterceptor {

    @Value("${flow.key}")
    private String flowKey;

    @Value("${flow.workflow.stopLiveEventNotifier.sig:}")
    private String flowSig;

    @Value("${flow.workflow.stopLiveEventNotifier.id}")
    private String flowId;

    @Override
    protected String getFlowKey() {
        return flowKey;
    }

    @Override
    protected String getFlowSig() {
        return flowSig;
    }

    @Override
    protected String getFlowId() {
        return flowId;
    }
}
