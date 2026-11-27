package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserDTOTest {
    @Test
    @DisplayName("UserDTO.appAccess should be sorted by last access (descending)")
    void testAppAccessSorting() {
        var userEntity = createUserEntity();
        var userDTO = new UserDTO(userEntity, null, null);

        var appAccess = userDTO.getAppAccess();

        assertEquals("Most recent", appAccess.get(0).getRole().getName());
        assertEquals("Super User", appAccess.get(1).getRole().getName());
        assertEquals("Level 2", appAccess.get(2).getRole().getName());
        assertEquals("Level 4", appAccess.get(3).getRole().getName());
        assertEquals("Super User", appAccess.get(4).getRole().getName());
        assertEquals("Level 4", appAccess.get(5).getRole().getName());
        assertEquals("Level 2", appAccess.get(6).getRole().getName());


    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("UserDTO.appAccess should filter out deleted access when filterOutInactiveAccess is true")
    void testAppAccessFiltering(boolean filterOutInactiveAccess) {
        User userEntity = createUserEntity();
        UserDTO userDTO = new UserDTO(userEntity, filterOutInactiveAccess, null);

        List<BaseAppAccessDTO> appAccess = userDTO.getAppAccess();
        if (filterOutInactiveAccess) {
            assertEquals(5, appAccess.size());
        } else {
            assertEquals(2, appAccess.size());
        }
    }

    @Test
    @DisplayName("UserDTO.appAccess should not filter out any access by default")
    void testAppAccessFiltering() {
        User userEntity = createUserEntity();
        UserDTO userDTO = new UserDTO(userEntity, null, null);
        assertThat(userDTO.getAppAccess()).hasSize(7);
    }

    @Test
    @DisplayName("Should correctly mark terms as accepted")
    void userDtoTermsAccepted() {
        var userEntity = createUserEntity();
        var latestAppTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "app content");
        var acceptance1 = HelperFactory.createUserTermsAccepted(
            userEntity,
            latestAppTerms,
            Timestamp.from(Instant.now())
        );
        var latestPortalTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "portal content");
        var acceptance2 = HelperFactory.createUserTermsAccepted(
            userEntity,
            latestPortalTerms,
            Timestamp.from(Instant.now())
        );
        userEntity.setUserTermsAccepted(Set.of(acceptance1, acceptance2));
        var userDto = new UserDTO(userEntity, Set.of(latestAppTerms, latestPortalTerms));

        var termsAccepted = userDto.getTermsAccepted();
        assertThat(termsAccepted.keySet()).hasSize(2);
        assertThat(Arrays.stream(termsAccepted.keySet().toArray()).sorted(Comparator.comparing(Object::toString))
                       .toArray()).isEqualTo(Arrays.stream(TermsAndConditionsType.values())
                                                 .sorted(Comparator.comparing(Object::toString)).toArray());
        assertThat(termsAccepted.get(TermsAndConditionsType.APP)).isTrue();
        assertThat(termsAccepted.get(TermsAndConditionsType.PORTAL)).isTrue();
    }

    @Test
    @DisplayName("Should correctly mark terms as not accepted when there is no app terms")
    void userDtoTermsNotAcceptedOnNoAppTerms() {
        var userEntity = createUserEntity();
        var latestPortalTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "portal content");
        var acceptance2 = HelperFactory.createUserTermsAccepted(
            userEntity,
            latestPortalTerms,
            Timestamp.from(Instant.now())
        );
        userEntity.setUserTermsAccepted(Set.of(acceptance2));
        var userDto = new UserDTO(userEntity, Set.of(latestPortalTerms));

        var termsAccepted = userDto.getTermsAccepted();
        assertThat(termsAccepted.keySet()).hasSize(2);
        assertThat(Arrays.stream(termsAccepted.keySet().toArray()).sorted(Comparator.comparing(Object::toString))
                       .toArray()).isEqualTo(Arrays.stream(TermsAndConditionsType.values())
                                                 .sorted(Comparator.comparing(Object::toString)).toArray());
        assertThat(termsAccepted.get(TermsAndConditionsType.APP)).isFalse();
        assertThat(termsAccepted.get(TermsAndConditionsType.PORTAL)).isTrue();
    }

    @Test
    @DisplayName("Should correctly mark terms as not accepted when acceptance is outdated")
    void userDtoTermsNotAcceptedRecently() {
        var userEntity = createUserEntity();
        var latestAppTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "app content");
        var acceptance1 = HelperFactory.createUserTermsAccepted(
            userEntity,
            latestAppTerms,
            Timestamp.from(Instant.now().minusSeconds(31536000)) // one year ago
        );
        var latestPortalTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "portal content");
        var acceptance2 = HelperFactory.createUserTermsAccepted(
            userEntity,
            latestPortalTerms,
            Timestamp.from(Instant.now().minusSeconds(31449600)) // 364 days
        );
        userEntity.setUserTermsAccepted(Set.of(acceptance1, acceptance2));
        var userDto = new UserDTO(userEntity, Set.of(latestAppTerms, latestPortalTerms));

        var termsAccepted = userDto.getTermsAccepted();
        assertThat(termsAccepted.keySet()).hasSize(2);
        assertThat(Arrays.stream(termsAccepted.keySet().toArray()).sorted(Comparator.comparing(Object::toString))
                       .toArray()).isEqualTo(Arrays.stream(TermsAndConditionsType.values())
                                                 .sorted(Comparator.comparing(Object::toString)).toArray());
        assertThat(termsAccepted.get(TermsAndConditionsType.APP)).isFalse();
        assertThat(termsAccepted.get(TermsAndConditionsType.PORTAL)).isTrue();
    }

    @Test
    @DisplayName("UserDTO constructor called with null value for terms and conditions")
    void userDtoLatestTermsNull() {
        var userEntity = createUserEntity();
        var userDto = new UserDTO(userEntity, null);

        var termsAccepted = userDto.getTermsAccepted();
        assertThat(termsAccepted.keySet()).hasSize(2);
        assertThat(Arrays.stream(termsAccepted.keySet().toArray()).sorted(Comparator.comparing(Object::toString))
                       .toArray()).isEqualTo(Arrays.stream(TermsAndConditionsType.values())
                                                 .sorted(Comparator.comparing(Object::toString)).toArray());
        assertThat(termsAccepted.get(TermsAndConditionsType.APP)).isFalse();
        assertThat(termsAccepted.get(TermsAndConditionsType.PORTAL)).isFalse();
    }

    private User createUserEntity() {
        var user = HelperFactory.createUser("Example", "Person", "example@example.com", null, null, null);

        var now = Instant.now();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example", "123");
        var access2 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Most recent"),
            true,
            null,
            Timestamp.from(now),
            true
        );
        var access5 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Super User"),
            true,
            null,
            Timestamp.from(now.minusSeconds(20)),
            true
        );
        var access6 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Super User"),
            true,
            null,
            null, // Never accessed: should be treated as the least recent access
            true
        );
        var access3 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Level 2"),
            true,
            null,
            Timestamp.from(now.minusSeconds(20)),
            false
        );
        var access1 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Level 4"),
            true,
            null,
            Timestamp.from(now.minusSeconds(20)),
            false
        );
        var access7 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Level 4"),
            false,
            null,
            Timestamp.from(now.minusSeconds(10)),
            false
        );
        var access4 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Level 2"),
            false,
            null,
            null,
            false
        );

        user.setAppAccess(Set.of(access2, access3, access1, access4, access5, access6, access7));

        return user;
    }
}
