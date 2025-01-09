package uk.gov.hmcts.reform.preapi.utils;

import lombok.experimental.UtilityClass;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

@UtilityClass
public class DateTimeUtils {
    public static final ZoneId TIME_ZONE = ZoneId.of("Europe/London");

    // Date Format DD/MM/YY
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
    // Time Format HH:MM:SS
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);

    public String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        return timestamp.toInstant().atZone(TIME_ZONE).format(DATE_FORMATTER);
    }

    public String formatTime(Timestamp timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        return timestamp.toInstant().atZone(TIME_ZONE).format(TIME_FORMATTER);
    }

    public boolean isDaylightSavings(Timestamp timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        return TIME_ZONE.getRules().isDaylightSavings(timestamp.toInstant());
    }

    public String getTimezoneAbbreviation(Timestamp timestamp) {
        return isDaylightSavings(timestamp) ? "BST" : "GMT";
    }
}
