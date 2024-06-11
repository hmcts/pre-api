package uk.gov.hmcts.reform.preapi.media;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaServiceBroker {

    IMediaService mediaService;

    @Autowired
    public MediaServiceBroker(
        @Value("${mediakind.enabled}") Boolean mediaKindEnabled,
        MediaKind mediaKind,
        AzureMediaService azureMediaService) {
        if (mediaKindEnabled) {
            this.mediaService = mediaKind;
        } else {
            this.mediaService = azureMediaService;
        }
    }

    public IMediaService getEnabledMediaService() {
        return mediaService;
    }
}
