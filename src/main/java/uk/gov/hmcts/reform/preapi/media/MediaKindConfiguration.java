package uk.gov.hmcts.reform.preapi.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class MediaKindConfiguration {

    private final String environmentTag;
    private final String subscription;
    private final String issuer;
    private final String symmetricKey;
    private final int jobPollingInterval;
    private final int streamingEndpointPollingInterval;
    private final String vodStreamingEndpoint;
    private final String liveStreamingEndpoint;
    private final String location;
    private final String streamingEndpointAdvancedSettingsName;

    @Autowired
    public MediaKindConfiguration(
        @Value("${platform-env}") String env,
        @Value("${mediakind.subscription}") String subscription,
        @Value("${mediakind.issuer:}") String issuer,
        @Value("${mediakind.symmetricKey:}") String symmetricKey,
        @Value("${mediakind.job-poll-interval}") int jobPollingInterval,
        @Value("${mediakind.streaming-endpoint-polling-interval}") int streamingEndpointPollingInterval,
        @Value("${mediakind.vodStreamingEndpoint}") String vodStreamingEndpoint,
        @Value("${mediakind.liveStreamingEndpoint}") String liveStreamingEndpoint,
        @Value("${mediakind.location}") String location,
        @Value("${mediakind.streaming-endpoint-advanced-settings-name:}") String streamingEndpointAdvancedSettingsName
    ) {
        this.environmentTag = env;
        this.subscription = subscription;
        this.issuer = issuer;
        this.symmetricKey = symmetricKey;
        this.jobPollingInterval = jobPollingInterval;
        this.streamingEndpointPollingInterval = streamingEndpointPollingInterval;
        this.vodStreamingEndpoint = vodStreamingEndpoint;
        this.liveStreamingEndpoint = liveStreamingEndpoint;
        this.location = location;
        this.streamingEndpointAdvancedSettingsName = streamingEndpointAdvancedSettingsName;
    }

}
