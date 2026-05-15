package uk.gov.hmcts.reform.preapi.reports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.ReportService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {UserFullAccessCsvReportGenerator.class})
class UserFullAccessCsvReportGeneratorTest {

    @MockitoBean
    private ReportService reportService;

    @Autowired
    private UserFullAccessCsvReportGenerator underTest;

    @Test
    @DisplayName("Should generate CSV report")
    void shouldGenerateCsvReport() {
        UserAccessReportDTO dto = HelperFactory.createUserAccessReportDTO("user 1 ");
        UserAccessReportDTO dto2 = HelperFactory.createUserAccessReportDTO("user 2 ");
        UserAccessReportDTO dto3 = HelperFactory.createUserAccessReportDTO("user 3 ");
        UserAccessReportDTO dto4 = HelperFactory.createUserAccessReportDTO("user 4 ");

        List<UserAccessReportDTO> writableObjects = List.of(dto, dto2, dto3, dto4);

        when(reportService.reportUserFullAccess()).thenReturn(writableObjects);

        Optional<String> result = underTest.getCsvReportAsString();

        assertThat(result).isPresent();

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo(
            """
                FIRST NAME,LAST NAME,PRIMARY EMAIL,ALTERNATIVE EMAIL,COURT NAME,ACCESS ROLE,ACCESS TYPE,ACTIVE
                Test,User,example@example.com,user-1-alt@email.co.uk,user 1 court name,user 1 role,Secondary,Active
                Test,User,example@example.com,user-2-alt@email.co.uk,user 2 court name,user 2 role,Secondary,Active
                Test,User,example@example.com,user-3-alt@email.co.uk,user 3 court name,user 3 role,Secondary,Active
                Test,User,example@example.com,user-4-alt@email.co.uk,user 4 court name,user 4 role,Secondary,Active
                """);
    }
}
