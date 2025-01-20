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
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

@Component
@Slf4j
public class RecordingListener {
    private final AzureFinalStorageService azureFinalStorageService;

    private final EmailServiceFactory emailServiceFactory;

    private final ShareBookingService shareBookingService;

    @Autowired
    public RecordingListener(@Lazy AzureFinalStorageService azureFinalStorageService,
                             @Lazy ShareBookingService shareBookingService,
                             EmailServiceFactory emailServiceFactory) {
        this.azureFinalStorageService = azureFinalStorageService;
        this.shareBookingService = shareBookingService;
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
            var shares = shareBookingService.getSharesForCase(r.getCaptureSession().getBooking().getCaseId());
            var emailService = emailServiceFactory.getEnabledEmailService();
            shares.forEach(share -> emailService.recordingReady(
                share.getSharedWith(), r.getCaptureSession().getBooking().getCaseId())
            );
        } catch (Exception e) {
            log.error("Failed to notify users of recording ready for recording: " + r.getId());
        }
    }
}
