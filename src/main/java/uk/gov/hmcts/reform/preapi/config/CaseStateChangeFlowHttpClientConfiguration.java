package uk.gov.hmcts.reform.preapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaseStateChangeFlowHttpClientConfiguration {
    @Bean
    public CaseStateChangeFlowRequestInterceptor caseStateChangeFlowRequestInterceptor() {
        return new CaseStateChangeFlowRequestInterceptor();
    }
}
