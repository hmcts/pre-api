package uk.gov.hmcts.reform.preapi.alerts;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Model that represents a section in the Slack message.
 */
@Data
@AllArgsConstructor
public class SlackMessageSection {
    private String title;
    private List<String> items;
    private String emptyMessage;
}
