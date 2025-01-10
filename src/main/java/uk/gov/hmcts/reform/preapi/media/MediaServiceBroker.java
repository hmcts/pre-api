package uk.gov.hmcts.reform.preapi.media;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class MediaServiceBroker {
    public static final String MEDIA_SERVICE_MK = "MediaKind";

    private final IMediaService mediaService;
    private final MediaKind mediaKind;

    @Autowired
    public MediaServiceBroker(@Value("${media-service}") String mediaServiceEnabled, MediaKind mediaKind) {
        this.mediaKind = mediaKind;

        if (Objects.equals(mediaServiceEnabled, MEDIA_SERVICE_MK)) {
            this.mediaService = mediaKind;
        } else {
            throw new IllegalArgumentException("Unknown media service: " + mediaServiceEnabled);
        }
    }

    public IMediaService getEnabledMediaService() {
        return mediaService;
    }

    public IMediaService getEnabledMediaService(String overrideMediaService) {
        switch (overrideMediaService) {
            case MEDIA_SERVICE_MK -> {
                return mediaKind;
            }
            case null -> {
                return getEnabledMediaService();
            }
            default -> throw new IllegalArgumentException("Unknown media service: " + overrideMediaService);
        }
    }
}
