package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserDTOTest {
    @DisplayName("UserDTO.appAccess should be sorted by last access (descending)")
    @Test
    public void testAppAccessSorting() {
        var userEntity = createUserEntity();
        var userDTO = new UserDTO(userEntity);

        var appAccess = userDTO.getAppAccess();
        assertEquals("Example Role 3", appAccess.get(0).getRole().getName());
        assertEquals("Example Role 2", appAccess.get(1).getRole().getName());
        assertEquals("Example Role 1", appAccess.get(2).getRole().getName());
    }

    private User createUserEntity() {
        var user = HelperFactory.createUser("Example", "Person", "example@example.com", null, null, null);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example", "123");
        var access2 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Example Role 1"),
            true,
            null,
            Timestamp.from(Instant.now())
        );
        var access3 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Example Role 2"),
            true,
            null,
            Timestamp.from(Instant.now())
        );
        var access1 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Example Role 3"),
            true,
            null,
            Timestamp.from(Instant.now())
        );

        user.setAppAccess(Set.of(access2, access3, access1));

        return user;
    }
}
