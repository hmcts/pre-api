package uk.gov.hmcts.reform.preapi.reports.shared;

import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.reports.UserFullAccessCsvReportGenerator;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CsvReportGenerator.class)
public class CsvReportGeneratorTest {

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

        Optional<String> result = underTest.generateCsvReport(columnOrder, writableObjects, UserAccessReportDTO.class);

        assertThat(result.isPresent());

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo("""
                                  TODO
                                  """);
    }
}
