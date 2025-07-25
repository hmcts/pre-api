package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.util.HashSet;
import java.util.Set;


@UtilityClass
public final class RecordingUtils {

    public static final Set<String> VALID_VERSION_TYPES = new HashSet<>();

    static {
        VALID_VERSION_TYPES.addAll(Constants.VALID_ORIG_TYPES);
        VALID_VERSION_TYPES.addAll(Constants.VALID_COPY_TYPES);
    }

    public static String normalizeVersionType(String input) {
        if (input == null) {
            return "ORIG";
        }

        String upper = input.trim().toUpperCase();
        if (Constants.VALID_ORIG_TYPES.contains(upper)) {
            return "ORIG";
        }
        if (Constants.VALID_COPY_TYPES.contains(upper)) {
            return "COPY";
        }
        return "ORIG"; 
    }


    public int getStandardizedVersionNumberFromType(String recordingVersion) {
        return Constants.VALID_ORIG_TYPES.contains(recordingVersion.toUpperCase()) ? 1 : 2;
    }

    public String getValidVersionNumber(String versionNumStr) {
        return (versionNumStr == null || versionNumStr.trim().isEmpty()) ? "1" : versionNumStr.trim();
    }

    public int compareVersionStrings(String v1, String v2) {
        if (v1 == null || v1.isBlank()) {
            v1 = "0";
        }
        if (v2 == null || v2.isBlank()) {
            v2 = "0";
        }

        String[] v1Parts = v1.split("\\.");
        String[] v2Parts = v2.split("\\.");

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

    public record VersionDetails(
        String extractedVersionType,             // "ORIG" or "COPY"
        String extractedVersionNumberStr,        // e.g. "1.2"
        String origVersionNumberStr,             // always set
        String copyVersionNumberStr,             // only for COPY recordings
        int standardisedVersionNumber,           // 1 for ORIG, 2 for COPY
        boolean isMostRecent
    ) {
    }

    
}