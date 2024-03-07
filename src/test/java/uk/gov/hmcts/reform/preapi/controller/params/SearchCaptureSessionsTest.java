package uk.gov.hmcts.reform.preapi.controller.params;


import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchCaptureSessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchCaptureSessionsTest {
    @Test
    public void testGetCaseReference() {
        var searchCaptureSessions = new SearchCaptureSessions();
        searchCaptureSessions.setCaseReference("ABC123");
        assertEquals("ABC123", searchCaptureSessions.getCaseReference());

        searchCaptureSessions.setCaseReference("");
        assertNull(searchCaptureSessions.getCaseReference());

        searchCaptureSessions.setCaseReference(null);
        assertNull(searchCaptureSessions.getCaseReference());
    }
}
