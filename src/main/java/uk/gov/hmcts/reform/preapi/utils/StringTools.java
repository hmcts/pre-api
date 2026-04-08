package uk.gov.hmcts.reform.preapi.utils;

import java.time.Duration;
import java.time.LocalTime;

import static java.lang.String.format;

public class StringTools {
    public static boolean isBlankString(String string) {
        return string == null || string.trim().isEmpty();
    }

    public static Integer formatTimeAsInteger(LocalTime localTime) {
        if (localTime == null) {
            return null;
        }
        return localTime.toSecondOfDay();

    }

    public static String formatDurationAsString(Duration duration) {
        if (duration == null) {
            return null;
        }
        long seconds = duration.toSeconds();
        return formatTimeAsString((int) seconds);
    }

    public static String formatTimeAsString(Integer time) {
        if (time < 0) {
            throw new IllegalArgumentException("Time in seconds cannot be negative: " + time);
        }

        Integer hours = time / 3600;
        Integer minutes = time % 3600 / 60;
        Integer seconds = time % 60;

        return format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static LocalTime formatTimeAsLocalTime(Integer time) {
        String timeString = formatTimeAsString(time);

        return LocalTime.parse(timeString);
    }
}
