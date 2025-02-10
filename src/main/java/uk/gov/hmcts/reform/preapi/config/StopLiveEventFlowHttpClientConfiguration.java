package uk.gov.hmcts.reform.preapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StopLiveEventFlowHttpClientConfiguration {

    @Bean
    public StopLiveEventFlowRequestInterceptor requestInterceptor() {
        return new StopLiveEventFlowRequestInterceptor();
    }
}
