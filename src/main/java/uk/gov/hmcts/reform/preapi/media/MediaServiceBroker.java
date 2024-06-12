package uk.gov.hmcts.reform.preapi.media;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class MediaServiceBroker {

    public static final String MEDIA_SERVICE_AMS = "AzureMediaService";
    public static final String MEDIA_SERVICE_MK = "MediaKind";

    IMediaService mediaService;

    @Autowired
    public MediaServiceBroker(
        @Value("${media-service}") String mediaServiceEnabled,
        MediaKind mediaKind,
        AzureMediaService azureMediaService) {
        if (Objects.equals(mediaServiceEnabled, MEDIA_SERVICE_MK)) {
            this.mediaService = mediaKind;
        } else if (Objects.equals(mediaServiceEnabled, MEDIA_SERVICE_AMS)) {
            this.mediaService = azureMediaService;
        } else {
            throw new IllegalArgumentException("Unknown media service: " + mediaServiceEnabled);
        }
    }

    public IMediaService getEnabledMediaService() {
        return mediaService;
    }
}
