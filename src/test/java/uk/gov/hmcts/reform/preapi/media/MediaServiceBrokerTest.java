package uk.gov.hmcts.reform.preapi.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MediaServiceBrokerTest {

    @Mock
    private MediaKind mediaKind;
    @Mock
    private AzureMediaService azureMediaService;

    @DisplayName("Should select MK as the media service")
    @Test
    void getMKMediaServiceSuccess() {

        var broker = new MediaServiceBroker(MediaServiceBroker.MEDIA_SERVICE_MK,
                                            mediaKind,
                                            azureMediaService);

        assertThat(broker.getEnabledMediaService()).isEqualTo(mediaKind);
    }

    @DisplayName("Should select AMS as the media service")
    @Test
    void getAMSMediaServiceSuccess() {

        var broker = new MediaServiceBroker(MediaServiceBroker.MEDIA_SERVICE_AMS,
                                            mediaKind,
                                            azureMediaService);

        assertThat(broker.getEnabledMediaService()).isEqualTo(azureMediaService);
    }

    @DisplayName("Should throw exception with no media service")
    @Test
    void unknownMediaService() {

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            new MediaServiceBroker("foobar",
                                   mediaKind,
                                   azureMediaService);
        });

        assertThat(ex.getMessage()).isEqualTo("Unknown media service: foobar");
    }
}
