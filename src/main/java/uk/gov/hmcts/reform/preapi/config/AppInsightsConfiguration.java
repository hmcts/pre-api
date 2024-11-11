package uk.gov.hmcts.reform.preapi.config;

import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppInsightsConfiguration {
    @Bean
    public TelemetryClient telemetryClient(
        @Value("${azure.application-insights.connection-string:}") String connectionString) {
        if (connectionString == null) {
            return null;
        }
        var client = new TelemetryClient();
        client.getContext().setConnectionString(connectionString);
        return client;
    }
}
