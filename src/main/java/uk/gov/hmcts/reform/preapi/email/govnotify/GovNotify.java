package uk.gov.hmcts.reform.preapi.email.govnotify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.*;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

@Slf4j
@Service
public class GovNotify implements IEmailService {
    private final NotificationClient client;
    private final String portalUrl;

    @Autowired
    public GovNotify(
        @Value("${email.govNotify.key}") String apikey,
        @Value("${portal.url}") String portalUrl
    ) {
        this.client = new NotificationClient(apikey);
        this.portalUrl = portalUrl;
    }

    @Override
    public void recordingReady(User to, Case forCase) {
        var template = new RecordingReady(to.getEmail(), to.getFirstName(), forCase.getReference(), forCase.getCourt().getName(), portalUrl);
        try {
            sendEmail(template);
            log.info("Recording ready email sent to {}", to.getEmail());
        } catch (NotificationClientException e) {
            log.error("Failed to send recording ready email to {}", to.getEmail(), e);
        }
    }

    @Override
    public void recordingEdited(User to, Case forCase) {
        var template = new RecordingEdited(to.getEmail(), to.getFirstName(), forCase.getReference(), forCase.getCourt().getName(), portalUrl);
        try {
            sendEmail(template);
            log.info("Recording edited email sent to {}", to.getEmail());
        } catch (NotificationClientException e) {
            log.error("Failed to send recording edited email to {}", to.getEmail(), e);
        }
    }

    @Override
    public void portalInvite(User to) {
        var template = new PortalInvite(to.getEmail(), to.getFirstName(), portalUrl, portalUrl + "/user-guide", portalUrl + "/process-guide", portalUrl + "/faqs");
        try {
            sendEmail(template);
            log.info("Portal invite email sent to {}", to.getEmail());
        } catch (NotificationClientException e) {
            log.error("Failed to send portal invite email to {}", to.getEmail(), e);
        }
    }

    @Override
    public void casePendingClosure(User to, Case forCase, String date) {
        var template = new CasePendingClosure(to.getEmail(), to.getFirstName(), to.getLastName(), forCase.getReference(), date);
        try {
            sendEmail(template);
            log.info("Case pending closure email sent to {}", to.getEmail());
        } catch (NotificationClientException e) {
            log.error("Failed to send case pending closure email to {}", to.getEmail(), e);
        }
    }

    @Override
    public void caseClosed(User to, Case forCase) {
        var template = new CaseClosed(to.getEmail(), to.getFirstName(), to.getLastName(), forCase.getReference());
        try {
            sendEmail(template);
            log.info("Case closed email sent to {}", to.getEmail());
        } catch (NotificationClientException e) {
            log.error("Failed to send case closed email to {}", to.getEmail(), e);
        }
    }

    @Override
    public void caseClosureCancelled(User to, Case forCase) {
        var template = new CaseClosureCancelled(to.getEmail(), to.getFirstName(), to.getLastName(), forCase.getReference());
        try {
            sendEmail(template);
            log.info("Case closure cancelled email sent to {}", to.getEmail());
        } catch (NotificationClientException e) {
            log.error("Failed to send case closure cancelled email to {}", to.getEmail(), e);
        }
    }

    private void sendEmail(BaseTemplate email) throws NotificationClientException {
        client.sendEmail(email.getTemplateId(), email.getTo(), email.getVariables(), email.getReference());
    }
}
