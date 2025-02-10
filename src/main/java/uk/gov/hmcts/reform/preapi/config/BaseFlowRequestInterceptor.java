package uk.gov.hmcts.reform.preapi.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;

public abstract class BaseFlowRequestInterceptor implements RequestInterceptor {
    @Value("${flow.key}")
    private String flowKey;

    @Override
    public void apply(RequestTemplate template) {
        if (!template.path().contains(getFlowId())) {
            return;
        }

        template.header("Content-Type", "application/json");
        template.header("X-Flow-Key", getFlowKey());

        template.query("api-version", "2016-06-01");
        template.query("sp", "%2Ftriggers%2Fmanual%2Frun");
        template.query("sv", "1.0");
        template.query("sig", getFlowSig());
    }

    protected String getFlowKey() {
        return flowKey;
    }

    protected abstract String getFlowSig();

    protected abstract String getFlowId();
}
