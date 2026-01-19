package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

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

    @Transactional
    public void onEditRequestSubmitted(EditRequest request) {
        Court court = request.getSourceRecording().getCaptureSession().getBooking().getCaseId().getCourt();
        if (court.getGroupEmail() == null) {
            log.error("Court {} does not have a group email for sending edit request submission email for request: {}",
                      court.getId(), request.getId());
            return;
        }

        IEmailService enabledEmailService;
        try {
            enabledEmailService = emailServiceFactory.getEnabledEmailService();
        } catch (IllegalArgumentException e) {
            log.error("Error sending email:  {}", e.getMessage());
            return;
        }

        String groupEmail = court.getGroupEmail();

        try {
            if (Boolean.TRUE.equals(request.getJointlyAgreed())) {
                enabledEmailService.editingJointlyAgreed(groupEmail, request);
            } else {
                enabledEmailService.editingNotJointlyAgreed(groupEmail, request);
            }
        } catch (Exception e) {
            log.error("Error sending email on edit request submission: {}", e.getMessage());
        }
    }

    @Transactional
    public void onEditRequestRejected(EditRequest request) {
        Court court = request.getSourceRecording().getCaptureSession().getBooking().getCaseId().getCourt();
        if (court.getGroupEmail() == null) {
            log.error("Court {} does not have a group email for sending edit request rejection email for request: {}",
                      court.getId(), request.getId());
            return;
        }

        try {
            emailServiceFactory.getEnabledEmailService().editingRejected(court.getGroupEmail(), request);
        } catch (Exception e) {
            log.error("Error sending email on edit request rejection: {}", e.getMessage());
        }
    }

}
