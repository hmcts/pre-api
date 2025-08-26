package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserDTOTest {
    @Test
    @DisplayName("UserDTO.appAccess should be sorted by last access (descending)")
    public void testAppAccessSorting() {
        var userEntity = createUserEntity();
        var userDTO = new UserDTO(userEntity, null);

        var appAccess = userDTO.getAppAccess();
        assertEquals("Example Role 3", appAccess.get(0).getRole().getName());
        assertEquals("Example Role 2", appAccess.get(1).getRole().getName());
        assertEquals("Example Role 1", appAccess.get(2).getRole().getName());
        assertEquals("Example Role 4", appAccess.get(3).getRole().getName());
    }

    @Test
    @DisplayName("Should correctly mark terms as accepted")
    public void userDtoTermsAccepted() {
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
    public void userDtoTermsNotAcceptedOnNoAppTerms() {
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
    public void userDtoTermsNotAcceptedRecently() {
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
    public void userDtoLatestTermsNull() {
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
            HelperFactory.createRole("Example Role 1"),
            true,
            null,
            Timestamp.from(now),
            true
        );
        var access3 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Example Role 2"),
            true,
            null,
            Timestamp.from(now.plusSeconds(10)),
            false
        );
        var access1 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Example Role 3"),
            true,
            null,
            Timestamp.from(now.plusSeconds(20)),
            false
        );
        var access4 = HelperFactory.createAppAccess(
            user,
            court,
            HelperFactory.createRole("Example Role 4"),
            true,
            null,
            null,
            false
        );

        user.setAppAccess(Set.of(access2, access3, access1, access4));

        return user;
    }
}
