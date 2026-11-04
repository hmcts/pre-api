package uk.gov.hmcts.reform.preapi.reports.shared;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CsvReportGenerator.class)
class CsvReportGeneratorTest {

    private static final List<UserAccessReportDTO> writableObjects = getInputObjects();

    @Qualifier("csvReportGenerator")
    @Autowired
    private CsvReportGenerator underTest;

    @Test
    @DisplayName("Should generate CSV report with specified column order")
    void shouldGenerateCsvReportInSpecifiedColumnOrder() {
        final List<String> columnOrder = List.of(
            "Court name", "Access role",
            "Access type", "Active",
            "First name", "Last name",
            "Alternative email",
            "Primary email"
        );

        Optional<String> result = underTest.generateCsvReport(
            columnOrder, writableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result).isPresent();

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo(getExpectedCsvReport());
    }

    @Test
    @DisplayName("Should be case insensitive")
    void shouldBeCaseInsensitive() {
        final List<String> columnOrder = List.of(
            "court name", "ACCESS ROLE",
            "Access type", "Active",
            "First name", "Last name",
            "ALTerNATive emAIL",
            "Primary email"
        );

        Optional<String> result = underTest.generateCsvReport(
            columnOrder, writableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result).isPresent();

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo(getExpectedCsvReport());
    }

    @Test
    @DisplayName("Should default to alphanumeric if no column order specified")
    void shouldDefaultToAlphanumericIfNoColumnOrderSpecified() {
        final List<String> columnOrder = List.of();

        Optional<String> result = underTest.generateCsvReport(
            columnOrder, writableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result).isPresent();

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv)
            .isEqualTo(
                """
                    ACCESS ROLE,ACCESS TYPE,ACTIVE,ALTERNATIVE EMAIL,COURT NAME,FIRST NAME,LAST NAME,PRIMARY EMAIL
                    user 1 role,Secondary,Active,user-1-alt@email.co.uk,user 1 court name,Test,User,example@example.com
                    user 2 role,Secondary,Active,user-2-alt@email.co.uk,user 2 court name,Test,User,example@example.com
                    user 3 role,Secondary,Active,user-3-alt@email.co.uk,user 3 court name,Test,User,example@example.com
                    user 4 role,Secondary,Active,user-4-alt@email.co.uk,user 4 court name,Test,User,example@example.com
                    """);
    }

    @Test
    @DisplayName("Should cope with empty list of writable objects")
    void shouldCopeWithEmptyList() {
        final List<UserAccessReportDTO> noWritableObjects = List.of();

        final List<String> columnOrder = List.of("not", "important");

        Optional<String> result = underTest.generateCsvReport(
            columnOrder,
            noWritableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should cope with null list of writable objects")
    void shouldCopeWithNullListOfWritableObjects() {
        final List<UserAccessReportDTO> nullWritableObjects = null;

        final List<String> columnOrder = List.of("not", "important");

        Optional<String> result = underTest.generateCsvReport(
            columnOrder,
            nullWritableObjects,
            UserAccessReportDTO.class
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Can create CSV for a different POJO")
    void canCreateCSVForDifferentPOJO() {
        final List<Court> randomCourts = List.of(
            HelperFactory.createCourt(CourtType.CROWN, "court one", null),
            HelperFactory.createCourt(CourtType.MAGISTRATE, "two", null)
        );

        final List<String> columnOrder = List.of("not", "important");

        Optional<String> result = underTest.generateCsvReport(
            columnOrder,
            randomCourts,
            Court.class
        );

        String csv = result.orElseThrow(() -> new NotFoundException("No CSV generated"));
        assertThat(csv).isEqualTo("""
                                      COUNTY,COURTTYPE,GROUPEMAIL,ID,LOCATIONCODE,NAME,POSTCODE,REGIONS
                                      ,CROWN,,,,court one,,
                                      ,MAGISTRATE,,,,two,,
                                      """);
    }

    private static @NotNull String getExpectedCsvReport() {
        return """
            COURT NAME,ACCESS ROLE,ACCESS TYPE,ACTIVE,FIRST NAME,LAST NAME,ALTERNATIVE EMAIL,PRIMARY EMAIL
            user 1 court name,user 1 role,Secondary,Active,Test,User,user-1-alt@email.co.uk,example@example.com
            user 2 court name,user 2 role,Secondary,Active,Test,User,user-2-alt@email.co.uk,example@example.com
            user 3 court name,user 3 role,Secondary,Active,Test,User,user-3-alt@email.co.uk,example@example.com
            user 4 court name,user 4 role,Secondary,Active,Test,User,user-4-alt@email.co.uk,example@example.com
            """;
    }

    private static @NotNull List<UserAccessReportDTO> getInputObjects() {
        UserAccessReportDTO dto = HelperFactory.createUserAccessReportDTO("user 1 ");
        UserAccessReportDTO dto2 = HelperFactory.createUserAccessReportDTO("user 2 ");
        UserAccessReportDTO dto3 = HelperFactory.createUserAccessReportDTO("user 3 ");
        UserAccessReportDTO dto4 = HelperFactory.createUserAccessReportDTO("user 4 ");

        return List.of(dto, dto2, dto3, dto4);
    }
}
