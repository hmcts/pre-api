package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccessDTOTest {
    @Test
    void accessDTOWithNullAppAccessAndPortalAccess() {
        var user = mock(User.class);
        when(user.getAppAccess()).thenReturn(null);
        when(user.getPortalAccess()).thenReturn(null);

        var accessDTO = new AccessDTO(user, null);

        assertEquals(0, accessDTO.getAppAccess().size());
        assertEquals(0, accessDTO.getPortalAccess().size());
        assertEquals(2, accessDTO.getTermsAccepted().size());
        assertEquals(false, accessDTO.getTermsAccepted().get(TermsAndConditionsType.APP));
        assertEquals(false, accessDTO.getTermsAccepted().get(TermsAndConditionsType.PORTAL));
    }

    @Test
    void accessDTOWithEmptyAppAccessAndPortalAccess() {
        var user = mock(User.class);

        var latestAppTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "app content");
        var acceptance1 = HelperFactory.createUserTermsAccepted(
            user,
            latestAppTerms,
            Timestamp.from(Instant.now())
        );
        var latestPortalTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "portal content");
        var acceptance2 = HelperFactory.createUserTermsAccepted(
            user,
            latestPortalTerms,
            Timestamp.from(Instant.now())
        );
        when(user.getUserTermsAccepted()).thenReturn(Set.of(acceptance1, acceptance2));

        when(user.getAppAccess()).thenReturn(Set.of());
        when(user.getPortalAccess()).thenReturn(Set.of());

        var accessDTO = new AccessDTO(user, Set.of(latestAppTerms, latestPortalTerms));

        assertEquals(0, accessDTO.getAppAccess().size());
        assertEquals(0, accessDTO.getPortalAccess().size());
        assertEquals(2, accessDTO.getTermsAccepted().size());
        assertEquals(true, accessDTO.getTermsAccepted().get(TermsAndConditionsType.APP));
        assertEquals(true, accessDTO.getTermsAccepted().get(TermsAndConditionsType.PORTAL));
    }

    @Test
    void accessDTOWithValidAppAccessAndPortalAccess() {
        var appAccess1 = new AppAccess();
        appAccess1.setDeleted(false);
        appAccess1.setActive(true);
        appAccess1.setCourt(HelperFactory.createCourt(CourtType.CROWN, "Example", ""));
        appAccess1.setRole(HelperFactory.createRole("ROLE"));

        var portalAccess1 = new PortalAccess();
        portalAccess1.setDeleted(false);
        portalAccess1.setStatus(AccessStatus.ACTIVE);

        var user = mock(User.class);

        when(user.getAppAccess()).thenReturn(Set.of(appAccess1));
        when(user.getPortalAccess()).thenReturn(Set.of(portalAccess1));

        var accessDTO = new AccessDTO(user, null);

        assertEquals(1, accessDTO.getAppAccess().size());
        assertEquals(1, accessDTO.getPortalAccess().size());
        assertEquals(2, accessDTO.getTermsAccepted().size());
        assertEquals(false, accessDTO.getTermsAccepted().get(TermsAndConditionsType.APP));
        assertEquals(false, accessDTO.getTermsAccepted().get(TermsAndConditionsType.PORTAL));
    }
}
