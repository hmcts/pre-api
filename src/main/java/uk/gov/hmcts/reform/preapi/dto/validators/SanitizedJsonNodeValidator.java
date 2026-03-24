package uk.gov.hmcts.reform.preapi.dto.validators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizerUtils;

import java.util.Map;

/**
 * Validator that checks JsonNode keys and string values for potentially malicious content.
 */
public class SanitizedJsonNodeValidator implements ConstraintValidator<SanitizedJsonNodeConstraint, JsonNode> {

    @Override
    public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
        if (value == null || value.isNull()) {
            return true;
        }

        return isNodeSanitized(value);
    }

    private boolean isNodeSanitized(JsonNode node) {
        if (node.isNull()) {
            return true;
        }

        if (node.isObject()) {
            return isObjectSanitized(node);
        }

        if (node.isArray()) {
            return isArraySanitized(node);
        }

        return node.isTextual() && isSanitized(node.textValue());
    }

    private boolean isSanitized(String value) {
        return value.equals(InputSanitizerUtils.sanitize(value));
    }

    private boolean isObjectSanitized(JsonNode node) {
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!isSanitized(field.getKey()) || !isNodeSanitized(field.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean isArraySanitized(JsonNode node) {
        for (JsonNode element : node) {
            if (!isNodeSanitized(element)) {
                return false;
            }
        }
        return true;
    }
}



