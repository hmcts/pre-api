package uk.gov.hmcts.reform.preapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.hmcts.reform.preapi.media.FeignErrorDecoder;

@Slf4j
public class MediaKindClientConfiguration {

    @Value("${mediakind.token}")
    private String mkToken;

    @Bean
    public MKRequestInterceptor requestInterceptor() {
        log.info("Creating MKRequestInterceptor with token: {}", mkToken);
        return new MKRequestInterceptor(mkToken);
    }

    @Bean
    @Primary
    Decoder feignDecoder(ObjectMapper objectMapper) {
        return new JacksonDecoder(objectMapper);
    }

    @Bean
    public FeignErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
