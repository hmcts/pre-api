package uk.gov.hmcts.reform.preapi.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class MKRequestInterceptor implements RequestInterceptor {

    private final String mkToken;

    public MKRequestInterceptor(String mkToken) {
        this.mkToken = mkToken;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        requestTemplate.header("Content-Type", "application/json");
        requestTemplate.header("x-mkio-token", mkToken);
    }
}
