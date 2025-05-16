package uk.gov.hmcts.reform.preapi.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        StringBuilder message = new StringBuilder();

        message.append(":globe_with_meridians: *Environment:* ")
                .append(environment)
                .append("\n\n");

        sections.forEach(section -> {
            message.append(":warning: *").append(section.getTitle()).append(":*\n\n");

            List<String> items = section.getItems();

            message.append(items.isEmpty()
                    ? "\t:white_check_mark: " + section.getEmptyMessage() + "\n\n"
                    : items.stream().map(item -> "\t:siren: " + item + " :siren:")
                    .collect(Collectors.joining("\n")) + "\n\n");
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
