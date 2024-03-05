package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchRecordingsTest {

    @Test
    public void testGetCaseReference() {
        var searchRecordings = new SearchRecordings();
        searchRecordings.setId("abc");
        assertEquals("abc", searchRecordings.getId());

        searchRecordings.setId("");
        assertNull(searchRecordings.getId());

        searchRecordings.setId(null);
        assertNull(searchRecordings.getId());
    }
}
