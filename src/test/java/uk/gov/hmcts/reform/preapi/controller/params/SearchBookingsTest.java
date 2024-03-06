package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchBookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchBookingsTest {
    @Test
    public void testGetCaseReference() {
        var searchBookings = new SearchBookings();
        searchBookings.setCaseReference("ABC123");
        assertEquals("ABC123", searchBookings.getCaseReference());

        searchBookings.setCaseReference("");
        assertNull(searchBookings.getCaseReference());

        searchBookings.setCaseReference(null);
        assertNull(searchBookings.getCaseReference());
    }
}
