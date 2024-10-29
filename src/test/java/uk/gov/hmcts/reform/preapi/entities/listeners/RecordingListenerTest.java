package uk.gov.hmcts.reform.preapi.entities.listeners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RecordingListener.class)
public class RecordingListenerTest {
    @MockBean
    private AzureFinalStorageService azureFinalStorageService;

    @Autowired
    private RecordingListener recordingListener;

    @Test
    @DisplayName("Should not query blob storage for duration when duration already set")
    void setDurationBeforePersistDurationCurrentlyNotNull() {
        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        recordingListener.setDurationBeforePersist(recording);

        assertThat(recording.getDuration()).isEqualTo(Duration.ofMinutes(3));

        verify(azureFinalStorageService, never()).getRecordingDuration(any());
    }

    @Test
    @DisplayName("Should query blob storage for duration when duration is null")
    void setDurationBeforePersistDurationCurrentlyNull() {
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        recordingListener.setDurationBeforePersist(recording);

        assertThat(recording.getDuration()).isEqualTo(Duration.ofMinutes(3));

        verify(azureFinalStorageService, times(1)).getRecordingDuration(recording.getId());
    }
}
