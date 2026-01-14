package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@Getter
public abstract class BaseTemplate {
    private final String to;
    private final String reference;
    private final Map<String, Object> variables;

    @Value("${portal.url}") String portalLink;

    public BaseTemplate(String to, Map<String, Object> variables) {
        this.to = to;
        this.reference = generateReference();
        this.variables = variables;
        this.variables.put("portal_link", portalLink);
    }

    public abstract String getTemplateId();

    private String generateReference() {
        return "ref";
    }

}
