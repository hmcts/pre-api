package uk.gov.hmcts.reform.preapi.utils;

import static java.lang.String.format;

public final class StringTools {
    private StringTools() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
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

}
