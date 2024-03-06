package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchInvites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchInvitesTest {
    @Test
    public void testGetFirstName() {
        var searchInvites = new SearchInvites();
        searchInvites.setFirstName("John");
        assertEquals("John", searchInvites.getFirstName());

        searchInvites.setFirstName("");
        assertNull(searchInvites.getFirstName());

        searchInvites.setFirstName(null);
        assertNull(searchInvites.getFirstName());
    }

    @Test
    public void testGetLastName() {
        var searchInvites = new SearchInvites();
        searchInvites.setLastName("Doe");
        assertEquals("Doe", searchInvites.getLastName());

        searchInvites.setLastName("");
        assertNull(searchInvites.getLastName());

        searchInvites.setLastName(null);
        assertNull(searchInvites.getLastName());
    }

    @Test
    public void testGetEmail() {
        var searchInvites = new SearchInvites();
        searchInvites.setEmail("john.doe@example.com");
        assertEquals("john.doe@example.com", searchInvites.getEmail());

        searchInvites.setEmail("");
        assertNull(searchInvites.getEmail());

        searchInvites.setEmail(null);
        assertNull(searchInvites.getEmail());
    }

    @Test
    public void testGetOrganisation() {
        var searchInvites = new SearchInvites();
        searchInvites.setOrganisation("ABC");
        assertEquals("ABC", searchInvites.getOrganisation());

        searchInvites.setOrganisation("");
        assertNull(searchInvites.getOrganisation());

        searchInvites.setOrganisation(null);
        assertNull(searchInvites.getOrganisation());
    }
}
