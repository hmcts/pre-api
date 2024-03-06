package uk.gov.hmcts.reform.preapi.controller.params;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchUsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SearchUsersTest {

    @Test
    public void getName() {
        var searchUsers = new SearchUsers();
        searchUsers.setName("John Doe");
        assertEquals("John Doe", searchUsers.getName());

        searchUsers.setName("");
        assertNull(searchUsers.getName());

        searchUsers.setName(null);
        assertNull(searchUsers.getName());
    }

    @Test
    public void getEmail() {
        var searchUsers = new SearchUsers();
        searchUsers.setEmail("john.doe@example.com");
        assertEquals("john.doe@example.com", searchUsers.getEmail());

        searchUsers.setEmail("");
        assertNull(searchUsers.getEmail());

        searchUsers.setEmail(null);
        assertNull(searchUsers.getEmail());
    }

    @Test
    public void getOrganisation() {
        var searchUsers = new SearchUsers();
        searchUsers.setOrganisation("ABC");
        assertEquals("ABC", searchUsers.getOrganisation());

        searchUsers.setOrganisation("");
        assertNull(searchUsers.getOrganisation());

        searchUsers.setOrganisation(null);
        assertNull(searchUsers.getOrganisation());
    }
}
