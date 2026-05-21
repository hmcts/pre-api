package uk.gov.hmcts.reform.preapi.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ListOfStringsCaseInsensitiveSorter.class)
class ListOfStringsCaseInsensitiveSorterTest {

    @Test
    @DisplayName("Should correctly order strings as specified")
    void shouldGenerateCsvReportInSpecifiedColumnOrder() {
        final List<String> columnOrder = List.of(
            "First", "Second", "Third"
        );

        ListOfStringsCaseInsensitiveSorter underTest = new ListOfStringsCaseInsensitiveSorter(columnOrder);

        List<String> sortStrings = Arrays.asList("Second", "First",  "Third");

        sortStrings.sort(underTest);

        assertThat(sortStrings).isEqualTo(columnOrder);
    }

    @Test
    @DisplayName("Should be case insensitive")
    void shouldBeCaseInsensitive() {
        final List<String> columnOrder = List.of(
            "First", "SECOND", "third"
        );

        ListOfStringsCaseInsensitiveSorter underTest = new ListOfStringsCaseInsensitiveSorter(columnOrder);

        List<String> sortStrings = Arrays.asList("Second", "First",  "Third");

        sortStrings.sort(underTest);

        assertThat(sortStrings).isEqualTo(List.of("First", "Second", "Third"));
    }

    @Test
    @DisplayName("Should default to returning unsorted if no column order specified")
    void shouldDefaultToAlphanumericIfNoColumnOrderSpecified() {
        ListOfStringsCaseInsensitiveSorter underTest = new ListOfStringsCaseInsensitiveSorter(List.of());

        List<String> sortStrings = Arrays.asList("Bananas", "Apples", "Pears");

        sortStrings.sort(underTest);

        assertThat(sortStrings).isEqualTo(List.of("Bananas", "Apples", "Pears"));
    }

    @Test
    @DisplayName("Should cope with sorting an empty list")
    void shouldCopeWithEmptyList() {
        final List<String> columnOrder = List.of(
            "not", "important", "for", "this", "test"
        );

        ListOfStringsCaseInsensitiveSorter underTest = new ListOfStringsCaseInsensitiveSorter(columnOrder);

        List<String> emptyList = new ArrayList<>();

        emptyList.sort(underTest);

        assertThat(emptyList).isEqualTo(List.of());
    }
}
