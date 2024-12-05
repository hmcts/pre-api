package uk.gov.hmcts.reform.preapi.email;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Component
public class EmailServiceFactory {

    private final Map<String, IEmailService> emailServices = new HashMap<>();

    IEmailService enabledEmailService;

    private final boolean isEnabled;

    @Autowired
    public EmailServiceFactory(
        @Value("${email.service}") String emailServiceName,
        @Value("${email.enable}") Boolean isEnabled,
        List<IEmailService> services) {

        for (IEmailService service : services) {
            emailServices.put(service.getClass().getSimpleName(), service);
        }

        this.enabledEmailService = getEnabledEmailService(emailServiceName);
        this.isEnabled = isEnabled;
    }

    public IEmailService getEnabledEmailService(String name) {
        if (emailServices.containsKey(name)) {
            return emailServices.get(name);
        } else {
            throw new IllegalArgumentException("Unknown email service: " + name);
        }
    }
}
