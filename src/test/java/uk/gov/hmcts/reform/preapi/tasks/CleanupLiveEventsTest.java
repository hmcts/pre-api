package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CleanupLiveEventsTest {

    @Test
    public void testRun() {
        // Arrange
        var mediaServiceBroker = mock(MediaServiceBroker.class);
        var mediaService = mock(AzureMediaService.class);
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setResourceState("Running");
        List<LiveEventDTO> liveEventDTOList = new ArrayList<>();
        liveEventDTOList.add(liveEventDTO);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEventDTOList);
        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker);

        // Act
        cleanupLiveEvents.run();

        // Assert
        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        // @todo uncomment this line when https://github.com/hmcts/pre-api/pull/579/ is merged
        // verify(mediaService, times(1)).stopLiveEvent(anyString());
    }
}
