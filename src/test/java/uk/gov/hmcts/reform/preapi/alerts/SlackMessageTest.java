package uk.gov.hmcts.reform.preapi.alerts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class SlackMessageTest {

    private SlackMessage slackMessage;

    @BeforeEach
    void setUp() {
        slackMessage = SlackMessage.builder()
                .environment("Test")
                .sections(Collections.emptyList())
                .build();
    }

    @Test
    void testConstructorAndGetters() {
        List<String> items = List.of("Item 1", "Item 2");
        SlackMessageSection section = new SlackMessageSection("Warning Section", items, "No issues");

        slackMessage = SlackMessage.builder()
                .environment("Production")
                .sections(List.of(section))
                .build();

        Assertions.assertEquals("Production", slackMessage.getEnvironment());
        Assertions.assertEquals(1, slackMessage.getSections().size());
        Assertions.assertEquals("Warning Section", slackMessage.getSections().getFirst().getTitle());
        Assertions.assertEquals(items, slackMessage.getSections().getFirst().getItems());
        Assertions.assertEquals("No issues", slackMessage.getSections().getFirst().getEmptyMessage());
    }

    @Test
    void testToJson() {
        List<String> items = List.of("Alert 1", "Alert 2");
        SlackMessageSection section = new SlackMessageSection("Important Alerts", items, "No alerts");

        slackMessage = SlackMessage.builder()
                .environment("Test")
                .sections(List.of(section))
                .build();

        String json = slackMessage.toJson();

        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains(":globe_with_meridians: *Environment:* Test"));
        Assertions.assertTrue(json.contains(":warning: *Important Alerts:*"));
        Assertions.assertTrue(json.contains("Alert 1"));
        Assertions.assertTrue(json.contains("Alert 2"));
    }

    @Test
    void testToJsonWithEmptyItems() {
        SlackMessageSection section = new SlackMessageSection(
                "No Alerts", Collections.emptyList(), "No alerts available");

        slackMessage = SlackMessage.builder()
                .environment("Test")
                .sections(List.of(section))
                .build();

        String json = slackMessage.toJson();

        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains(":globe_with_meridians: *Environment:* Test"));
        Assertions.assertTrue(json.contains(":warning: *No Alerts:*"));
        Assertions.assertTrue(json.contains(":white_check_mark: No alerts available"));
    }

    @Test
    void testToJsonWithNullValues() {
        slackMessage = SlackMessage.builder()
                .environment(null)
                .sections(Collections.emptyList())
                .build();

        String json = slackMessage.toJson();

        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains(":globe_with_meridians: *Environment:* null"));
    }

    @Test
    void testToJsonWithMultipleSections() {
        SlackMessageSection section1 = new SlackMessageSection("Section 1", List.of("Item A", "Item B"), "No items");
        SlackMessageSection section2 = new SlackMessageSection("Section 2", List.of("Item C"), "No issues");

        slackMessage = SlackMessage.builder()
                .environment("Dev")
                .sections(List.of(section1, section2))
                .build();

        String json = slackMessage.toJson();

        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains(":globe_with_meridians: *Environment:* Dev"));
        Assertions.assertTrue(json.contains(":warning: *Section 1:*"));
        Assertions.assertTrue(json.contains("Item A"));
        Assertions.assertTrue(json.contains("Item B"));
        Assertions.assertTrue(json.contains(":warning: *Section 2:*"));
        Assertions.assertTrue(json.contains("Item C"));
    }

    @Test
    void testToJsonWithEmptySections() {
        slackMessage = SlackMessage.builder()
                .environment("Test")
                .sections(Collections.emptyList())
                .build();

        String json = slackMessage.toJson();

        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains(":globe_with_meridians: *Environment:* Test"));
        Assertions.assertFalse(json.contains(":warning:"));
    }
}
