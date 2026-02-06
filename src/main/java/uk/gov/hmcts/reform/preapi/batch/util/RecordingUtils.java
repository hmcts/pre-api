package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@UtilityClass
public class RecordingUtils {
    public static final Set<String> VALID_VERSION_TYPES = new HashSet<>();

    static {
        VALID_VERSION_TYPES.addAll(Constants.VALID_ORIG_TYPES);
        VALID_VERSION_TYPES.addAll(Constants.VALID_COPY_TYPES);
    }

    public static String normalizeVersionType(String input) {
        if (input == null) {
            return "ORIG";
        }

        String upper = input.trim().toUpperCase(Locale.UK);
        if (Constants.VALID_ORIG_TYPES.contains(upper)) {
            return "ORIG";
        }
        if (Constants.VALID_COPY_TYPES.contains(upper)) {
            return "COPY";
        }
        return "ORIG";
    }

    public int getStandardizedVersionNumberFromType(String recordingVersion) {
        return Constants.VALID_ORIG_TYPES.contains(recordingVersion.toUpperCase(Locale.UK)) ? 1 : 2;
    }

    public String getValidVersionNumber(String versionNumStr) {
        return (versionNumStr == null || versionNumStr.isBlank()) ? "1" : versionNumStr.trim();
    }

    public int compareVersionStrings(final String v1, final String v2) {
        final String versionString1 = v1 == null || v1.isBlank() ? "0" : v1;
        final String versionString2 = v2 == null || v2.isBlank() ? "0" : v2;

        final String[] v1Parts = versionString1.split("\\.");
        final String[] v2Parts = versionString2.split("\\.");

        int length = Math.max(v1Parts.length, v2Parts.length);
        for (int i = 0; i < length; i++) {
            int v1Part = (i < v1Parts.length) ? parseVersionPart(v1Parts[i]) : 0;
            int v2Part = (i < v2Parts.length) ? parseVersionPart(v2Parts[i]) : 0;
            if (v1Part < v2Part) {
                return -1;
            }

            if (v1Part > v2Part) {
                return 1;
            }
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Optional<LocalDateTime> parseDatePatternToLocalDateTime(String datePattern) {
        if (datePattern == null || datePattern.isBlank()) {
            return Optional.empty();
        }
        String trimmed = datePattern.trim();
        try {
            if (trimmed.matches("\\d{6}")) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyMMdd"));
                return Optional.of(date.atTime(12, 0));
            }
            if (trimmed.matches("\\d{2}-\\d{2}-\\d{4}-\\d{4}")) {
                return Optional.of(LocalDateTime.parse(trimmed,
                    DateTimeFormatter.ofPattern("dd-MM-yyyy-HHmm")));
            }
            if (trimmed.matches("\\d{2}-\\d{2}-\\d{4}")) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                return Optional.of(date.atTime(12, 0));
            }
            if (trimmed.matches("\\d{2}/\\d{2}/\\d{4}")) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return Optional.of(date.atTime(12, 0));
            }
            if (trimmed.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                return Optional.of(date.atTime(12, 0));
            }
            return Optional.empty();
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public record VersionDetails(String extractedVersionType,             // "ORIG" or "COPY"
                                 String extractedVersionNumberStr,        // e.g. "1.2"
                                 String origVersionNumberStr,             // always set
                                 String copyVersionNumberStr,             // only for COPY recordings
                                 int standardisedVersionNumber,           // 1 for ORIG, 2 for COPY
                                 boolean isMostRecent) {
    }
}
