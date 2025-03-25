package uk.gov.hmcts.reform.preapi.alerts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class SlackMessageSectionTest {

    @Test
    void testConstructorAndGetters() {
        List<String> items = List.of("Item1", "Item2");
        SlackMessageSection section = new SlackMessageSection("Title", items, "No items available");

        Assertions.assertEquals("Title", section.getTitle());
        Assertions.assertEquals(items, section.getItems());
        Assertions.assertEquals("No items available", section.getEmptyMessage());
    }

    @Test
    void testSetters() {
        SlackMessageSection section = new SlackMessageSection("Title", Collections.emptyList(), "No items");

        section.setTitle("New Title");
        section.setItems(List.of("NewItem1", "NewItem2"));
        section.setEmptyMessage("Nothing to show");

        Assertions.assertEquals("New Title", section.getTitle());
        Assertions.assertEquals(List.of("NewItem1", "NewItem2"), section.getItems());
        Assertions.assertEquals("Nothing to show", section.getEmptyMessage());
    }

    @Test
    void testEmptyItemsList() {
        SlackMessageSection section = new SlackMessageSection("Title", Collections.emptyList(), "No items available");

        Assertions.assertTrue(section.getItems().isEmpty());
        Assertions.assertEquals("No items available", section.getEmptyMessage());
    }

    @Test
    void testNullValues() {
        SlackMessageSection section = new SlackMessageSection(null, null, null);

        Assertions.assertNull(section.getTitle());
        Assertions.assertNull(section.getItems());
        Assertions.assertNull(section.getEmptyMessage());
    }
}
