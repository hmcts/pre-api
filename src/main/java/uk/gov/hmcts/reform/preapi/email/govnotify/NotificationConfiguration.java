package uk.gov.hmcts.reform.preapi.email.govnotify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;

@Configuration
public class NotificationConfiguration {
    @Bean
    public NotificationClient notificationClient(
        @Value("${email.govNotify.key}") String apiKey
    ) {
        return new NotificationClient(apiKey);
    }
}
