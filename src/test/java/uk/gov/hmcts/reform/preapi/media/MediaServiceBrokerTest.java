package uk.gov.hmcts.reform.preapi.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MediaServiceBrokerTest {

    @Mock
    private MediaKind mediaKind;

    @DisplayName("Should select MK as the media service")
    @Test
    void getMKMediaServiceSuccess() {

        var broker = new MediaServiceBroker(MediaServiceBroker.MEDIA_SERVICE_MK, mediaKind);

        assertThat(broker.getEnabledMediaService()).isEqualTo(mediaKind);
    }

    @DisplayName("Should throw exception with no media service")
    @Test
    void unknownMediaService() {

        Exception ex = assertThrows(IllegalArgumentException.class, () -> new MediaServiceBroker("foobar", mediaKind));

        assertThat(ex.getMessage()).isEqualTo("Unknown media service: foobar");
    }

    @DisplayName("Should select MK as the media service when overridden")
    @Test
    void getMKMediaServiceSuccessOverride() {

        var broker = new MediaServiceBroker(MediaServiceBroker.MEDIA_SERVICE_MK, mediaKind);

        assertThat(broker.getEnabledMediaService(MediaServiceBroker.MEDIA_SERVICE_MK)).isEqualTo(mediaKind);
    }

    @DisplayName("Should return default when override value is null")
    @Test
    void getMediaServiceOverrideNull() {
        var broker = new MediaServiceBroker(MediaServiceBroker.MEDIA_SERVICE_MK, mediaKind);
        assertThat(broker.getEnabledMediaService(null)).isEqualTo(mediaKind);
    }

    @DisplayName("Should throw exception with no media service when overridden")
    @Test
    void unknownMediaServiceOverride() {

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            var broker = new MediaServiceBroker(MediaServiceBroker.MEDIA_SERVICE_MK, mediaKind);
            broker.getEnabledMediaService("foobar");
        });

        assertThat(ex.getMessage()).isEqualTo("Unknown media service: foobar");
    }
}
