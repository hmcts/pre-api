package uk.gov.hmcts.reform.preapi.reports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {UserFullAccessCsvReportGenerator.class})
public class UserFullAccessCsvReportGeneratorTest {

    @MockitoBean
    private ReportService reportService;

    @Autowired
    private UserFullAccessCsvReportGenerator underTest;

    @Test
    @DisplayName("Should generate CSV report")
    public void shouldGenerateCsvReport() {
        final List<String> columnOrder = List.of(
            "First name", "Last name",
            "Primary email", "Additional email",
            "Court name", "Access role",
            "Access type", "Active"
        );

        List<UserAccessReportDTO> writableObjects = List.of(
            new UserAccessReportDTO("first", "user", "primary@email",
                                    "additional@email.co.uk", "court name", true,
                                    "Level 1", true),
            new UserAccessReportDTO("first", "user", "primary@email",
                                    "additional@email.co.uk", "other court", false,
                                    "Level 4", true),
            new UserAccessReportDTO("second", "user", "primary@email",
                                    "additional@email.co.uk", "court name", true,
                                    "Level 1", false),
            new UserAccessReportDTO("third", "user", "primary@email",
                                    "additional@email.co.uk", "court name", true,
                                    "Level 1", true)
        );

        when(reportService.reportUserFullAccess()).thenReturn(writableObjects);

        Optional<String> result = underTest.generateCsvReport(columnOrder, writableObjects, UserAccessReportDTO.class);

        assertThat(result.isPresent());

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo(
"""
FIRST NAME,LAST NAME,PRIMARY EMAIL,ADDITIONAL EMAIL,COURT NAME,ACCESS ROLE,ACCESS TYPE,ACTIVE
first,user,primary@email,additional@email.co.uk,court name,Level 1,Primary,Active
first,user,primary@email,additional@email.co.uk,other court,Level 4,Secondary,Active
second,user,primary@email,additional@email.co.uk,court name,Level 1,Primary,Inactive
third,user,primary@email,additional@email.co.uk,court name,Level 1,Primary,Active
""");
    }
}
