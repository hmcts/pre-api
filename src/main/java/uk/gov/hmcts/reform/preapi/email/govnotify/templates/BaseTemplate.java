package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class BaseTemplate {
    private final String to;
    private final String reference;
    private final Map<String, Object> variables;

    @Value("${portal.url}") String portalLink;

    public BaseTemplate(String to, Map<String, Object> inputVariables) {
        this.to = to;
        this.reference = generateReference();
        this.variables = getVariables(inputVariables);
    }

    public abstract String getTemplateId();

    private String generateReference() {
        return "ref";
    }

    private Map<String, Object> getVariables(Map<String, Object> inputVariables) {
        Map<String, Object> variablesMap = new HashMap<>(inputVariables);
        variablesMap.put("portal_link", portalLink);
        return variablesMap;
    }

}
