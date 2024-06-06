package uk.gov.hmcts.reform.preapi.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaKindClientConfiguration {

    @Value("${mediakind.token}")
    private String mkToken;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Content-Type", "application/json");
            requestTemplate.header("x-mkio-token", mkToken);
        };
    }
}
