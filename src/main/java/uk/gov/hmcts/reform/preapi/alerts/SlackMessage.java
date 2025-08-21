package uk.gov.hmcts.reform.preapi.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Slf4j
public class SlackMessage {

    private String environment;

    private List<SlackMessageSection> sections;

    /**
     * Converts the contents of the model to a JSON string.
     *
     * @return A string which is the message content.
     */
    @SuppressWarnings("PMD.InsufficientStringBufferDeclaration")
    public String toJson() {
        return toJson(SlackMessageJsonOptions.builder()
                          .showEnvironment(true)
                          .showIcons(true)
                          .build());
    }

    /**
     * Converts the contents of the model to a JSON string.
     *
     * @param options Options for the message.
     * @return A string which is the message content.
     */
    public String toJson(SlackMessageJsonOptions options) {
        boolean showEnvironment = options.showEnvironment();
        boolean showIcons = options.showIcons();

        StringBuilder message = new StringBuilder();

        if (showEnvironment) {
            message.append(":globe_with_meridians: *Environment:* ")
                .append(environment)
                .append("\n\n");
        }

        sections.forEach(section -> {
            if (showIcons) {
                message.append(":warning:");
            }
            message
                .append(" *")
                .append(section.getTitle()).append(":*\n");

            List<String> items = section.getItems();

            message.append(items.isEmpty()
                    ? (showIcons ? "\t:white_check_mark: " : "") + section.getEmptyMessage() + "\n\n"
                    : String.join("\n", items) + "\n\n");
        });

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(Map.of("text", message.toString()));
        } catch (JsonProcessingException ex) {
            log.error("Conversion to JSON for slack message failed, error: {}", ex.getMessage());
            return "";
        }
    }
}
