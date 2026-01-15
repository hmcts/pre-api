package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Getter;

import java.util.Map;

@Getter
public abstract class BaseTemplate {
    private final String to;
    private final String reference;
    private final Map<String, Object> variables;

    public BaseTemplate(String to, Map<String, Object> variables) {
        this.to = to;
        this.reference = generateReference();
        this.variables = variables;
    }

    public abstract String getTemplateId();

    private String generateReference() {
        return "ref";
    }

}
