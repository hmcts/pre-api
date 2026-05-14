package uk.gov.hmcts.reform.preapi.dto.reports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAccessReportDTOTest {

    @Test
    void standardFieldsShouldBeSet() {
        UserAccessReportDTO userAccessReportDTO = new UserAccessReportDTO("First", "Last",
                                                                          "primary@email",
                                                                          "additional@email",
                                                                          "court name",
                                                                          true, "role",
                                                                          true);

        assertThat(userAccessReportDTO.getFirstName()).isEqualTo("First");
        assertThat(userAccessReportDTO.getLastName()).isEqualTo("Last");
        assertThat(userAccessReportDTO.getPrimaryEmail()).isEqualTo("primary@email");
        assertThat(userAccessReportDTO.getAdditionalEmail()).isEqualTo("additional@email");
        assertThat(userAccessReportDTO.getCourtName()).isEqualTo("court name");
        assertThat(userAccessReportDTO.getRoleName()).isEqualTo("role");

        assertThat(userAccessReportDTO.getAccessType()).isEqualTo("Primary");
        assertThat(userAccessReportDTO.getActive()).isEqualTo("Active");

        UserAccessReportDTO dtoWithFalseBooleans = new UserAccessReportDTO("First", "Last",
                                                                          "primary@email",
                                                                          "additional@email",
                                                                          "court name",
                                                                          false, "role",
                                                                          false);

        assertThat(dtoWithFalseBooleans.getAccessType()).isEqualTo("Secondary");
        assertThat(dtoWithFalseBooleans.getActive()).isEqualTo("Inactive");
    }
}
