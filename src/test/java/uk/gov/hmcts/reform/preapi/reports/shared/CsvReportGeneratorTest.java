package uk.gov.hmcts.reform.preapi.reports.shared;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CsvReportGenerator.class)
public class CsvReportGeneratorTest {

    private static final List<UserAccessReportDTO> writableObjects = getInputObjects();

    @Qualifier("csvReportGenerator")
    @Autowired
    private CsvReportGenerator underTest;

    @Test
    @DisplayName("Should generate CSV report with specified column order")
    public void shouldGenerateCsvReportInSpecifiedColumnOrder() {
        final List<String> columnOrder = List.of(
            "Additional email",
            "Court name", "Access role",
            "Access type", "Active",
            "First name", "Last name",
            "Primary email"
        );

        Optional<String> result = underTest.generateCsvReport(
            columnOrder, writableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result.isPresent());

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo(getExpectedCsvReport());
    }

    @Test
    @DisplayName("Should be case insensitive")
    public void shouldBeCaseInsensitive() {
        final List<String> columnOrder = List.of(
            "ADDItionaL emAIL",
            "court name", "ACCESS ROLE",
            "Access type", "Active",
            "First name", "Last name",
            "Primary email"
        );

        Optional<String> result = underTest.generateCsvReport(
            columnOrder, writableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result.isPresent());

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo(getExpectedCsvReport());
    }

    @Test
    @DisplayName("Should default to alphanumeric if no column order specified")
    public void shouldDefaultToAlphanumericIfNoColumnOrderSpecified() {
        final List<String> columnOrder = List.of();

        Optional<String> result = underTest.generateCsvReport(
            columnOrder, writableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result.isPresent());

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv)
            .isEqualTo("""
                           ACCESS ROLE,ACCESS TYPE,ACTIVE,ADDITIONAL EMAIL,COURT NAME,FIRST NAME,LAST NAME,PRIMARY EMAIL
                           Level 1,Primary,Active,additional@email.co.uk,court name,first,user,primary@email
                           Level 4,Secondary,Active,additional@email.co.uk,other court,first,user,primary@email
                           Level 1,Primary,Inactive,additional@email.co.uk,court name,second,user,primary@email
                           Level 1,Primary,Active,additional@email.co.uk,court name,third,user,primary@email
                           """);
    }

    @Test
    @DisplayName("Should cope with empty list")
    public void shouldCopeWithEmptyList() {
        final List<String> columnOrder = List.of(
            "Additional email",
            "Court name", "Access role",
            "Access type", "Active",
            "First name", "Last name",
            "Primary email"
        );

        Optional<String> result = underTest.generateCsvReport(columnOrder, List.of(), UserAccessReportDTO.class);

        assertThat(result.isEmpty());
    }

    private static @NotNull String getExpectedCsvReport() {
        return """
            ADDITIONAL EMAIL,COURT NAME,ACCESS ROLE,ACCESS TYPE,ACTIVE,FIRST NAME,LAST NAME,PRIMARY EMAIL
            additional@email.co.uk,court name,Level 1,Primary,Active,first,user,primary@email
            additional@email.co.uk,other court,Level 4,Secondary,Active,first,user,primary@email
            additional@email.co.uk,court name,Level 1,Primary,Inactive,second,user,primary@email
            additional@email.co.uk,court name,Level 1,Primary,Active,third,user,primary@email
            """;
    }

    private static @NotNull List<UserAccessReportDTO> getInputObjects() {
        return List.of(
            new UserAccessReportDTO(
                "first", "user", "primary@email",
                "additional@email.co.uk", "court name", true,
                "Level 1", true
            ),
            new UserAccessReportDTO(
                "first", "user", "primary@email",
                "additional@email.co.uk", "other court", false,
                "Level 4", true
            ),
            new UserAccessReportDTO(
                "second", "user", "primary@email",
                "additional@email.co.uk", "court name", true,
                "Level 1", false
            ),
            new UserAccessReportDTO(
                "third", "user", "primary@email",
                "additional@email.co.uk", "court name", true,
                "Level 1", true
            )
        );
    }
}
