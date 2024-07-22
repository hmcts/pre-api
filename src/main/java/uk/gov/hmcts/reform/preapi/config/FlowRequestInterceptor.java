package uk.gov.hmcts.reform.preapi.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class FlowRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        requestTemplate.header("Content-Type", "application/json");
        requestTemplate.query("api-version", "2016-06-01");
        requestTemplate.query("sp", "/triggers/manual/run");
        requestTemplate.query("sv", "1.0");
        requestTemplate.query("sig", "ovPj3WyTwNU5Q2EwSiIrJ222iH8M11CTtPVu8O3-ZSQ");
    }
}
