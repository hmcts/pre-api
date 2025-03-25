package uk.gov.hmcts.reform.preapi.entities.listeners;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

import java.util.Set;
import java.util.stream.Stream;

@Component
@Slf4j
public class RecordingListener {
    private final AzureFinalStorageService azureFinalStorageService;

    private final EmailServiceFactory emailServiceFactory;

    @Autowired
    public RecordingListener(@Lazy AzureFinalStorageService azureFinalStorageService,
                             EmailServiceFactory emailServiceFactory) {
        this.azureFinalStorageService = azureFinalStorageService;
        this.emailServiceFactory = emailServiceFactory;
    }

    @PrePersist
    public void setDurationBeforePersist(Recording recording) {
        if (recording.getDuration() != null) {
            return;
        }
        recording.setDuration(azureFinalStorageService.getRecordingDuration(recording.getId()));
    }

    @PostPersist
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onRecordingCreated(Recording r) {
        log.info("onRecordingCreated: Recording({})", r.getId());
        if (!emailServiceFactory.isEnabled()) {
            return;
        }

        try {

            var shares = r.getCaptureSession().getBooking().getShares()
                          .stream()
                          .filter(s -> !s.isDeleted())
                          .toList();
            var emailService = emailServiceFactory.getEnabledEmailService();

            shares.forEach(share -> {
                if (Stream.ofNullable(share.getBooking().getCaptureSessions())
                      .flatMap(Set::stream)
                      .anyMatch(c -> c.getStatus().equals(RecordingStatus.RECORDING_AVAILABLE))) {
                    if (r.getVersion() > 1) {
                        emailService.recordingEdited(
                            share.getSharedWith(), r.getCaptureSession().getBooking().getCaseId());
                    } else {
                        emailService.recordingReady(
                            share.getSharedWith(), r.getCaptureSession().getBooking().getCaseId());
                        }
                    }
                }
            );
        } catch (Exception e) {
            log.error("Failed to notify users of recording ready for recording: " + r.getId());
        }
    }
}
