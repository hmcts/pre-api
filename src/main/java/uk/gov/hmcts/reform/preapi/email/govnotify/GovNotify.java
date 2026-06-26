package uk.gov.hmcts.reform.preapi.email.govnotify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.email.EmailResponse;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.BaseTemplate;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosureCancelled;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CasePendingClosure;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EditingJointlyAgreed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EditingNotJointlyAgreed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EditingRejection;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EmailVerification;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.PortalInvite;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingEdited;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingReady;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.EmailFailedToSendException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.preapi.media.edit.EditInstructions.fromJson;

@Slf4j
@Service
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class GovNotify implements IEmailService {
    private final NotificationClient client;
    private final String portalUrl;

    @Autowired
    public GovNotify(
        @Value("${portal.url}") String portalUrl,
        NotificationClient client
    ) {
        this.client = client;
        this.portalUrl = portalUrl;
    }

    @Override
    public EmailResponse recordingReady(User to, Case forCase) {
        String email = getUsersPreferredEmail(to);
        RecordingReady template = new RecordingReady(
            email,
            to.getFirstName(),
            to.getLastName(),
            forCase.getReference(),
            forCase.getCourt().getName(),
            portalUrl
        );
        try {
            log.info("Recording ready email sent to {}", email);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send recording ready email to {}", email, e);
            throw (EmailFailedToSendException) new EmailFailedToSendException(email).initCause(e);
        }
    }

    @Override
    public EmailResponse recordingEdited(User to, Case forCase) {
        String email = getUsersPreferredEmail(to);
        RecordingEdited template = new RecordingEdited(
            email,
            to.getFirstName(),
            to.getLastName(),
            forCase.getReference(),
            forCase.getCourt().getName(),
            portalUrl
        );
        try {
            log.info("Recording edited email sent to {}", email);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send recording edited email to {}", email, e);
            throw (EmailFailedToSendException) new EmailFailedToSendException(email).initCause(e);
        }
    }

    @Override
    public EmailResponse portalInvite(User to) {
        PortalInvite template = new PortalInvite(to.getEmail(), to.getFirstName(), to.getLastName(), portalUrl,
                                        portalUrl + "/assets/files/user-guide.pdf",
                                        portalUrl + "/assets/files/process-guide.pdf",
                                        portalUrl + "/assets/files/faqs.pdf",
                                        portalUrl + "/assets/files/pre-editing-request-form.xlsx");
        try {
            log.info("Portal invite email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send portal invite email to {}", to.getEmail(), e);
            throw (EmailFailedToSendException) new EmailFailedToSendException(to.getEmail()).initCause(e);
        }
    }

    @Override
    public EmailResponse casePendingClosure(User to, Case forCase, Timestamp date) {
        CasePendingClosure template = new CasePendingClosure(to.getEmail(), to.getFirstName(), to.getLastName(),
                                              forCase.getReference(), date);
        try {
            log.info("Case pending closure email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send case pending closure email to {}", to.getEmail(), e);
            throw (EmailFailedToSendException) new EmailFailedToSendException(to.getEmail()).initCause(e);
        }
    }

    @Override
    public EmailResponse caseClosed(User to, Case forCase) {
        CaseClosed template = new CaseClosed(
            to.getEmail(),
            to.getFirstName(),
            to.getLastName(),
            forCase.getReference()
        );
        try {
            log.info("Case closed email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send case closed email to {}", to.getEmail(), e);
            throw (EmailFailedToSendException) new EmailFailedToSendException(to.getEmail()).initCause(e);
        }
    }

    @Override
    public EmailResponse caseClosureCancelled(User to, Case forCase) {
        CaseClosureCancelled template = new CaseClosureCancelled(to.getEmail(), to.getFirstName(), to.getLastName(),
                                                forCase.getReference());
        try {
            log.info("Case closure cancelled email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send case closure cancelled email to {}", to.getEmail(), e);
            throw (EmailFailedToSendException) new EmailFailedToSendException(to.getEmail()).initCause(e);
        }
    }

    @Override
    public EmailResponse emailVerification(String email, String firstName, String lastName, String verificationCode) {
        EmailVerification template = new EmailVerification(email, firstName, lastName, verificationCode);
        try {
            log.info("Email verification sent to {}", email);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send email verification to {}", email, e);
            throw new EmailFailedToSendException(email, e);
        }
    }

    @Override
    public EmailResponse editingJointlyAgreed(String to, EditRequest editRequest) {
        Booking booking = editRequest.getSourceRecording().getCaptureSession().getBooking();
        List<EditCutInstructionDTO> requestInstructions = fromJson(editRequest.getEditInstruction())
            .getRequestedInstructions();

        String witnessName = booking.getWitnessName();
        String defendant = booking.getDefendantName();

        EditingJointlyAgreed template = new EditingJointlyAgreed(
            to,
            booking.getCaseId().getReference(),
            requestInstructions.size(),
            booking.getCaseId().getCourt().getName(),
            witnessName,
            defendant,
            generateEditSummary(requestInstructions),
            portalUrl
        );

        try {
            log.info("Edit request jointly agreed email sent to {}", to);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send edit request jointly agreed email to {}", to, e);
            throw new EmailFailedToSendException(to, e);
        }
    }

    @Override
    public EmailResponse editingNotJointlyAgreed(String to, EditRequest editRequest) {
        Booking booking = editRequest.getSourceRecording().getCaptureSession().getBooking();
        List<EditCutInstructionDTO> requestInstructions = fromJson(editRequest.getEditInstruction())
            .getRequestedInstructions();

        String witnessName = booking.getWitnessName();

        String defendant = booking.getDefendantName();

        EditingNotJointlyAgreed template = new EditingNotJointlyAgreed(
            to,
            booking.getCaseId().getReference(),
            requestInstructions.size(),
            booking.getCaseId().getCourt().getName(),
            witnessName,
            defendant,
            generateEditSummary(requestInstructions),
            portalUrl
        );

        try {
            log.info("Edit request not jointly agreed email sent to {}", to);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send edit request not jointly agreed email to {}", to, e);
            throw new EmailFailedToSendException(to, e);
        }
    }

    @Override
    public EmailResponse editingRejected(String to, EditRequest editRequest) {
        Booking booking = editRequest.getSourceRecording().getCaptureSession().getBooking();
        List<EditCutInstructionDTO> requestInstructions = fromJson(editRequest.getEditInstruction())
            .getRequestedInstructions();

        String witnessName = booking.getWitnessName();
        String defendant = booking.getDefendantName();
        EditingRejection template = new EditingRejection(
            to,
            booking.getCaseId().getReference(),
            editRequest.getRejectionReason(),
            booking.getCaseId().getCourt().getName(),
            witnessName,
            defendant,
            generateEditSummary(requestInstructions),
            editRequest.getJointlyAgreed(),
            portalUrl
        );

        try {
            log.info("Edit request rejection email sent to {}", to);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send edit request rejection email to {}", to, e);
            throw new EmailFailedToSendException(to, e);
        }
    }

    private SendEmailResponse sendEmail(BaseTemplate email) throws NotificationClientException {
        return client.sendEmail(email.getTemplateId(), email.getTo(), email.getVariables(), email.getReference());
    }

    private String generateEditSummary(List<EditCutInstructionDTO> editInstructions) {
        StringJoiner summary = new StringJoiner("");
        for (int i = 0; i < editInstructions.size(); i++) {
            EditCutInstructionDTO instruction = editInstructions.get(i);
            summary.add(format("Edit %s: %n", i + 1))
                .add(format("Start time: %s%n", instruction.getStartOfCut()))
                .add(format("End time: %s%n", instruction.getEndOfCut()))
                .add(format("Time Removed: %s%n", calculateTimeRemoved(instruction)))
                .add(format("Reason: %s%n%n", instruction.getReason()));
        }

        return summary.toString();
    }

    private String calculateTimeRemoved(EditCutInstructionDTO instruction) {
        long difference = instruction.getEnd() - instruction.getStart();
        Duration duration = Duration.ofSeconds(difference);

        return format("%02d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    // If the users alternative email ends with .cjsm.net then use that as the preferred email, else fall back
    // to the email field.
    private String getUsersPreferredEmail(User user) {
        if (user.getAlternativeEmail() != null && user.getAlternativeEmail().endsWith(".cjsm.net")) {
            return user.getAlternativeEmail();
        }
        return user.getEmail();
    }
}
