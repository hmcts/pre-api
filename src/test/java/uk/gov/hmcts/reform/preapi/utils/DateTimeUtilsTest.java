package uk.gov.hmcts.reform.preapi.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Month;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateTimeUtilsTest {

    @Test
    void formatDateSuccess() {
        var timestamp = Timestamp.from(Instant.now());
        var result = DateTimeUtils.formatDate(timestamp);

        assertThat(result).isNotNull();
        assertThat(result).matches("^\\d{2}/\\d{2}/\\d{4}$");
    }

    @Test
    void formatDateNull() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.formatDate(null));
    }

    @Test
    void formatTimeSuccess() {
        var timestamp = Timestamp.from(Instant.now());
        var result = DateTimeUtils.formatTime(timestamp);

        assertThat(result).isNotNull();
        assertThat(result).matches("^\\d{2}:\\d{2}:\\d{2}$");
    }

    @Test
    void formatTimeNull() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.formatTime(null));
    }

    @Test
    void getTimezoneAbbreviationBST() {
        var timestamp = Timestamp.from(ZonedDateTime.of(2025, 7, 15, 0, 0, 0, 0, DateTimeUtils.TIME_ZONE).toInstant());
        assertThat(DateTimeUtils.formatTime(timestamp)).isEqualTo("00:00:00");
        assertThat(DateTimeUtils.getTimezoneAbbreviation(timestamp)).isEqualTo("BST");
    }

    @Test
    void getTimezoneAbbreviationGMT() {
        var timestamp = Timestamp.from(ZonedDateTime.of(2025, 12, 15, 0, 0, 0, 0, DateTimeUtils.TIME_ZONE).toInstant());
        assertThat(DateTimeUtils.formatTime(timestamp)).isEqualTo("00:00:00");
        assertThat(DateTimeUtils.getTimezoneAbbreviation(timestamp)).isEqualTo("GMT");
    }

    @ParameterizedTest
    @DisplayName("Should correctly format local time based on timezone")
    @EnumSource(Month.class)
    void formatTimeUTCOrBST(Month month) {
        var timestamp = Timestamp.from(ZonedDateTime.of(2025, month.getValue(), 15,
                                                        3, 16, 42, 0,
                                                        DateTimeUtils.TIME_ZONE).toInstant());
        assertThat(DateTimeUtils.formatTime(timestamp)).isEqualTo("03:16:42");
        if (month.getValue() > 3 && month.getValue() <= 10) {
            assertThat(DateTimeUtils.getTimezoneAbbreviation(timestamp)).isEqualTo("BST");
        } else {
            assertThat(DateTimeUtils.getTimezoneAbbreviation(timestamp)).isEqualTo("GMT");
        }
    }

    @Test
    void getTimezoneAbbreviationNull() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.getTimezoneAbbreviation(null));
    }

    @Test
    void formatDurationSuccess() {
        var duration = java.time.Duration.ofHours(1).plusMinutes(30).plusSeconds(45);
        var result = DateTimeUtils.formatDuration(duration);

        assertThat(result).isNotNull().isEqualTo("01:30:45");
    }

    @Test
    void formatDurationNull() {
        var result = DateTimeUtils.formatDuration(null);

        assertThat(result).isNotNull().isEqualTo("00:00:00");
    }
}
