package uk.gov.hmcts.reform.preapi.controllers.params;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchAuditsTest {

    private SearchAudits search;

    @BeforeEach
    void setUp() {
        search = new SearchAudits();
    }

    @Test
    void testGetFunctionalArea_WhenNotNullOrEmpty() {
        search.setFunctionalArea("FunctionalArea");
        assertEquals("FunctionalArea", search.getFunctionalArea());
    }

    @Test
    void testGetFunctionalArea_WhenNull() {
        search.setFunctionalArea(null);
        assertNull(search.getFunctionalArea());
    }

    @Test
    void testGetFunctionalArea_WhenEmpty() {
        search.setFunctionalArea("");
        assertNull(search.getFunctionalArea());
    }

    @Test
    void testGetUserName_WhenNotNullOrEmpty() {
        search.setUserName("Example Person");
        assertEquals("Example Person", search.getUserName());
    }

    @Test
    void testGetUserName_WhenNull() {
        search.setUserName(null);
        assertNull(search.getUserName());
    }

    @Test
    void testGetUserName_WhenEmpty() {
        search.setUserName("");
        assertNull(search.getUserName());
    }

    @Test
    void testGetCaseReference_WhenNotNullOrEmpty() {
        search.setCaseReference("CASE12345");
        assertEquals("CASE12345", search.getCaseReference());
    }

    @Test
    void testGetCaseReference_WhenNull() {
        search.setCaseReference(null);
        assertNull(search.getCaseReference());
    }

    @Test
    void testGetCaseReference_WhenEmpty() {
        search.setCaseReference("");
        assertNull(search.getCaseReference());
    }
}
