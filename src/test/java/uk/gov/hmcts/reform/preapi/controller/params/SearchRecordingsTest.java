package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchRecordingsTest {

    @Test
    public void getCaseReference() {
        var searchRecordings = new SearchRecordings();
        searchRecordings.setCaseReference("123");
        assertEquals("123", searchRecordings.getCaseReference());

        searchRecordings.setCaseReference("");
        assertNull(searchRecordings.getCaseReference());

        searchRecordings.setCaseReference(null);
        assertNull(searchRecordings.getCaseReference());
    }

    @Test
    public void getWitnessName() {
        var searchRecordings = new SearchRecordings();
        searchRecordings.setWitnessName("John Doe");
        assertEquals("John Doe", searchRecordings.getWitnessName());

        searchRecordings.setWitnessName("");
        assertNull(searchRecordings.getWitnessName());

        searchRecordings.setWitnessName(null);
        assertNull(searchRecordings.getWitnessName());
    }

    @Test
    public void getDefendantName() {
        var searchRecordings = new SearchRecordings();
        searchRecordings.setDefendantName("Alice");
        assertEquals("Alice", searchRecordings.getDefendantName());

        searchRecordings.setDefendantName("");
        assertNull(searchRecordings.getDefendantName());

        searchRecordings.setDefendantName(null);
        assertNull(searchRecordings.getDefendantName());
    }

    @Test
    public void getId() {
        var searchRecordings = new SearchRecordings();
        searchRecordings.setId("abc");
        assertEquals("abc", searchRecordings.getId());

        searchRecordings.setId("");
        assertNull(searchRecordings.getId());

        searchRecordings.setId(null);
        assertNull(searchRecordings.getId());
    }
}
