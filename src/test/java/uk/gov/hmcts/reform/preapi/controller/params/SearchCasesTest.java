package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchCases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchCasesTest {
    @Test
    public void testGetReference() {
        var searchRecordings = new SearchCases();
        searchRecordings.setReference("ABC123");
        assertEquals("ABC123", searchRecordings.getReference());

        searchRecordings.setReference("");
        assertNull(searchRecordings.getReference());

        searchRecordings.setReference(null);
        assertNull(searchRecordings.getReference());
    }
}
