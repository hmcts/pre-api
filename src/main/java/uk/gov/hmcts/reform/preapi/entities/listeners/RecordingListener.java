package uk.gov.hmcts.reform.preapi.entities.listeners;

import jakarta.persistence.PrePersist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;

@Component
public class RecordingListener {
    private final AzureFinalStorageService azureFinalStorageService;

    @Autowired
    public RecordingListener(@Lazy AzureFinalStorageService azureFinalStorageService) {
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @PrePersist
    public void setDurationBeforePersist(Recording recording) {
        if (recording.getDuration() != null) {
            return;
        }
        recording.setDuration(azureFinalStorageService.getRecordingDuration(recording.getId()));
    }
}
