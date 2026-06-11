package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

@Service
@Slf4j
public class EditNotificationService {
    private final EmailServiceFactory emailServiceFactory;

    @Autowired
    public EditNotificationService(final EmailServiceFactory emailServiceFactory) {
        this.emailServiceFactory = emailServiceFactory;
    }

    @Transactional
    public void sendNotifications(Booking booking) {
        booking.getShares()
            .stream()
            .map(ShareBooking::getSharedWith)
            .forEach(u -> emailServiceFactory.getEnabledEmailService().recordingEdited(u, booking.getCaseId()));
    }

    public void editRequestStatusWasUpdated(EditRequest editRequest) {
        if (editRequest == null) {
            log.error("Tried to send email notification about a null edit request");
            return;
        }

        if (editRequest.getStatus() != EditRequestStatus.SUBMITTED
            && editRequest.getStatus() != EditRequestStatus.REJECTED) {
            // For edited recordings, notification is sent by RecordingListener instead
            log.info("No notification needed for edit request status {}", editRequest.getStatus().name());
        }

        try {
            emailServiceFactory.getEnabledEmailService().sendEmailAboutEditingRequest(editRequest);
        } catch (Exception e) {
            log.error("Error sending email on edit request submission: {}", e.getMessage());
        }
    }

}
