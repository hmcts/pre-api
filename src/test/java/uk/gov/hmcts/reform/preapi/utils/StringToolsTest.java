package uk.gov.hmcts.reform.preapi.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class StringToolsTest {

    @Test
    @DisplayName("Should parse and render time as strings")
    void should_parse_and_render_time_as_strings() {
        String parsedTime = StringTools.formatTimeAsString(35);
        assertThat(parsedTime).isEqualTo("00:00:35");
    }

    @Test
    @DisplayName("Should complain if time is negative")
    void should_complain_if_time_is_negative() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> StringTools.formatTimeAsString(-3));
    }

    @Test
    @DisplayName("Should cope with big time")
    void should_cope_with_big_time() {
        String parsedTime = StringTools.formatTimeAsString(3569429);
        assertThat(parsedTime).isEqualTo("991:30:29");
    }

    @Test
    @DisplayName("Should cope with zero time")
    void should_cope_with_zero_time() {
        String parsedTime = StringTools.formatTimeAsString(0);
        assertThat(parsedTime).isEqualTo("00:00:00");
    }
}
