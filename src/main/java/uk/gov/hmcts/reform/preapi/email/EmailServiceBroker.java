package uk.gov.hmcts.reform.preapi.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;

import java.util.Objects;

@Service
public class EmailServiceBroker {

    public static final String GOV_NOTIFY = "govnotify";

    IEmailService emailService;
    GovNotify govNotify;

    private final boolean enable;

    @Autowired
    public EmailServiceBroker(
        @Value("${email.service}") String emailServiceEnabled,
        @Value("${email.enable}") Boolean enable,
        GovNotify govNotify) {
        this.govNotify = govNotify;
        this.enable = enable;

        if (Objects.equals(emailServiceEnabled, GOV_NOTIFY)) {
            this.emailService = govNotify;
        } else {
            throw new IllegalArgumentException("Unknown email service: " + emailServiceEnabled);
        }
    }

    public IEmailService getEnabledEmailService() {
        return emailService;
    }

    public IEmailService getEnabledEmailService(String overrideEmailService) {
        switch (overrideEmailService) {
            case GOV_NOTIFY -> {
                return govNotify;
            }
            case null -> {
                return getEnabledEmailService();
            }
            default -> throw new IllegalArgumentException("Unknown email service: " + overrideEmailService);
        }
    }

    public boolean isEnabled() {
        return enable;
    }
}
