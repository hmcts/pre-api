package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Getter;

import java.util.Map;

@Getter
public abstract class BaseTemplate {
    private final String templateId = "";
    private final String to;
    private final String reference;
    private final Map<String, Object> variables;

    public BaseTemplate(String to, Map<String, Object> variables) {
        this.to = to;
        this.reference = generateReference();
        this.variables = variables;
    }

    public String getTemplateId() {
        if (!templateId.isEmpty()) {
            return templateId;
        }

        throw new IllegalStateException("Template ID is empty, set it in the child class");
    }

    private String generateReference() {
        return "ref";
    }

}
