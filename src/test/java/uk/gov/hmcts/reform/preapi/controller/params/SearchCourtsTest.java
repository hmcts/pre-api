package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchCourts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchCourtsTest {
    @Test
    public void testGetName() {
        var searchCourts = new SearchCourts();
        searchCourts.setName("John Doe");
        assertEquals("John Doe", searchCourts.getName());

        searchCourts.setName("");
        assertNull(searchCourts.getName());

        searchCourts.setName(null);
        assertNull(searchCourts.getName());
    }

    @Test
    public void testGetLocationCode() {
        var searchCourts = new SearchCourts();
        searchCourts.setLocationCode("ABC123");
        assertEquals("ABC123", searchCourts.getLocationCode());

        searchCourts.setLocationCode("");
        assertNull(searchCourts.getLocationCode());

        searchCourts.setLocationCode(null);
        assertNull(searchCourts.getLocationCode());
    }

    @Test
    public void testGetRegionName() {
        var searchCourts = new SearchCourts();
        searchCourts.setRegionName("North");
        assertEquals("North", searchCourts.getRegionName());

        searchCourts.setRegionName("");
        assertNull(searchCourts.getRegionName());

        searchCourts.setRegionName(null);
        assertNull(searchCourts.getRegionName());
    }
}
