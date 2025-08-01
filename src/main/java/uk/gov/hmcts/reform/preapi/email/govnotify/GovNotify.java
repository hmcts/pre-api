package uk.gov.hmcts.reform.preapi.email.govnotify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.email.EmailResponse;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.BaseTemplate;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosureCancelled;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CasePendingClosure;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.PortalInvite;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingEdited;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingReady;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.EmailFailedToSendException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.sql.Timestamp;

@Slf4j
@Service
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
        var template = new RecordingReady(to.getEmail(), to.getFirstName(), to.getLastName(), forCase.getReference(),
                                          forCase.getCourt().getName(), portalUrl);
        try {
            log.info("Recording ready email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send recording ready email to {}", to.getEmail(), e);
            throw new EmailFailedToSendException(to.getEmail());
        }
    }

    @Override
    public EmailResponse recordingEdited(User to, Case forCase) {
        var template = new RecordingEdited(to.getEmail(), to.getFirstName(), to.getLastName(), forCase.getReference(),
                                           forCase.getCourt().getName(), portalUrl);
        try {
            log.info("Recording edited email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send recording edited email to {}", to.getEmail(), e);
            throw new EmailFailedToSendException(to.getEmail());
        }
    }

    @Override
    public EmailResponse portalInvite(User to) {
        var template = new PortalInvite(to.getEmail(), to.getFirstName(), to.getLastName(), portalUrl,
                                        portalUrl + "/assets/files/user-guide.pdf",
                                        portalUrl + "/assets/files/process-guide.pdf",
                                        portalUrl + "/assets/files/faqs.pdf",
                                        portalUrl + "/assets/files/pre-editing-request-form.xlsx");
        try {
            log.info("Portal invite email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send portal invite email to {}", to.getEmail(), e);
            throw new EmailFailedToSendException(to.getEmail());
        }
    }

    @Override
    public EmailResponse casePendingClosure(User to, Case forCase, Timestamp date) {
        var template = new CasePendingClosure(to.getEmail(), to.getFirstName(), to.getLastName(),
                                              forCase.getReference(), date);
        try {
            log.info("Case pending closure email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send case pending closure email to {}", to.getEmail(), e);
            throw new EmailFailedToSendException(to.getEmail());
        }
    }

    @Override
    public EmailResponse caseClosed(User to, Case forCase) {
        var template = new CaseClosed(to.getEmail(), to.getFirstName(), to.getLastName(), forCase.getReference());
        try {
            log.info("Case closed email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send case closed email to {}", to.getEmail(), e);
            throw new EmailFailedToSendException(to.getEmail());
        }
    }

    @Override
    public EmailResponse caseClosureCancelled(User to, Case forCase) {
        var template = new CaseClosureCancelled(to.getEmail(), to.getFirstName(), to.getLastName(),
                                                forCase.getReference());
        try {
            log.info("Case closure cancelled email sent to {}", to.getEmail());
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send case closure cancelled email to {}", to.getEmail(), e);
            throw new EmailFailedToSendException(to.getEmail());
        }
    }

    @Override
    public EmailResponse emailVerification(String email, String firstName, String lastName, String verificationCode) {
        var template = new uk.gov.hmcts.reform.preapi.email.govnotify.templates.EmailVerification(
            email, firstName, lastName, verificationCode
        );
        try {
            log.info("Email verification sent to {}", email);
            return EmailResponse.fromGovNotifyResponse(sendEmail(template));
        } catch (NotificationClientException e) {
            log.error("Failed to send email verification to {}", email, e);
            throw new EmailFailedToSendException(email);
        }
    }

    private SendEmailResponse sendEmail(BaseTemplate email) throws NotificationClientException {
        return client.sendEmail(email.getTemplateId(), email.getTo(), email.getVariables(), email.getReference());
    }
}
