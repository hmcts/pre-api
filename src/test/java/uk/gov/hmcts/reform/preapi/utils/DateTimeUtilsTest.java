package uk.gov.hmcts.reform.preapi.utils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DateTimeUtilsTest {

    @Test
    public void formatDateSuccess() {
        var timestamp = Timestamp.from(Instant.now());
        var result = DateTimeUtils.formatDate(timestamp);

        assertThat(result).isNotNull();
        assertThat(result).matches("^\\d{2}/\\d{2}/\\d{4}$");
    }

    @Test
    public void formatDateNull() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.formatDate(null));
    }

    @Test
    public void formatTimeSuccess() {
        var timestamp = Timestamp.from(Instant.now());
        var result = DateTimeUtils.formatTime(timestamp);

        assertThat(result).isNotNull();
        assertThat(result).matches("^\\d{2}:\\d{2}:\\d{2}$");
    }

    @Test
    public void formatTimeNull() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.formatTime(null));
    }

    @Test
    public void getTimezoneAbbreviationBST() {
        var timestamp = Timestamp.from(ZonedDateTime.of(2025, 7, 15, 0, 0, 0, 0, DateTimeUtils.TIME_ZONE).toInstant());
        assertThat(DateTimeUtils.getTimezoneAbbreviation(timestamp)).isEqualTo("BST");
    }

    @Test
    public void getTimezoneAbbreviationGMT() {
        var timestamp = Timestamp.from(ZonedDateTime.of(2025, 12, 15, 0, 0, 0, 0, DateTimeUtils.TIME_ZONE).toInstant());
        assertThat(DateTimeUtils.getTimezoneAbbreviation(timestamp)).isEqualTo("GMT");
    }

    @Test
    public void getTimezoneAbbreviationNull() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeUtils.getTimezoneAbbreviation(null));
    }
}
