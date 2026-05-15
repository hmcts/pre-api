package uk.gov.hmcts.reform.preapi.dto.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccessReportDTOTest {

    private AppAccess appAccess;

    @BeforeEach
    public void setUp() {
        final User user = HelperFactory.createUser(
                "First", "Last",
                "primary@email", null, "phone", null
        );
        user.setAlternativeEmail("additional@email");

        final Court court = HelperFactory.createCourt(CourtType.CROWN, "court name", null);

        final Role role = HelperFactory.createRole("role");

        appAccess = new AppAccess();
        appAccess.setUser(user);
        appAccess.setCourt(court);
        appAccess.setRole(role);
    }

    @Test
    void standardFieldsShouldBeSet() {
        UserAccessReportDTO userAccessReportDTO = new UserAccessReportDTO(appAccess);
        assertThat(userAccessReportDTO.getFirstName()).isEqualTo("First");
        assertThat(userAccessReportDTO.getLastName()).isEqualTo("Last");
        assertThat(userAccessReportDTO.getPrimaryEmail()).isEqualTo("primary@email");
        assertThat(userAccessReportDTO.getAlternativeEmail()).isEqualTo("additional@email");
        assertThat(userAccessReportDTO.getCourtName()).isEqualTo("court name");
        assertThat(userAccessReportDTO.getRoleName()).isEqualTo("role");
    }

    @Test
    void calculatedFieldsShouldBeSetWhenFalse() {
        appAccess.setDefaultCourt(false);
        appAccess.setActive(false);
        UserAccessReportDTO userAccessReportDTO = new UserAccessReportDTO(appAccess);
        assertThat(userAccessReportDTO.getAccessType()).isEqualTo("Secondary");
        assertThat(userAccessReportDTO.getActive()).isEqualTo("Inactive");
    }

    @Test
    void calculatedFieldsShouldBeSetWhenTrue() {
        appAccess.setActive(true);
        appAccess.setDefaultCourt(true);

        UserAccessReportDTO userAccessReportDTO = new UserAccessReportDTO(appAccess);
        assertThat(userAccessReportDTO.getAccessType()).isEqualTo("Primary");
        assertThat(userAccessReportDTO.getActive()).isEqualTo("Active");
    }
}
