package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@UtilityClass
public final class RecordingUtils {

    public record VersionDetails(
        String extractedVersionType,               // "ORIG" or "COPY"
        String extractedVersionNumberStr,         // e.g. "1", "2"
        int standardisedVersionNumber,               // normalized: 1 for ORIG, 2 for COPY
        boolean isMostRecent             // calculated from cache
    ) {
        @Override
        public String toString() {
            return String.format(
                "VersionDetails[type=%s, number=%s, versionNum=%d, isMostRecent=%b]",
                extractedVersionType, extractedVersionNumberStr, standardisedVersionNumber, isMostRecent
            );
        }
    }

    public VersionDetails processVersioning(
        String extractedRecordingVersion,             // e.g. "ORIG", "COPY"
        String extractedVersionNumberStr,             // extracted from suffix: e.g. "1", "2"
        String urn,
        String defendant,
        String witness,
        Map<String, Object> existingCacheData // used to determine most recent
    ) {
        Map<String, Object> dataMap = existingCacheData != null ? existingCacheData : Collections.emptyMap();

        String extractedVersionType = Optional.ofNullable(extractedRecordingVersion)
            .map(String::toUpperCase)
            .filter(Constants.VALID_VERSION_TYPES::contains)
            .orElseThrow(() -> new IllegalArgumentException("Invalid recording version: " + extractedRecordingVersion));

        int standardizedVersionNumber = getStandardizedVersionNumberFromType(extractedVersionType);

        boolean isMostRecent = isMostRecentVersion(extractedVersionType, extractedVersionNumberStr, dataMap);

        return new VersionDetails(extractedVersionType, extractedVersionNumberStr, 
            standardizedVersionNumber, isMostRecent);
    }

    public int getStandardizedVersionNumberFromType(String recordingVersion) {
        return Constants.VALID_ORIG_TYPES.contains(recordingVersion.toUpperCase()) ? 1 : 2;
    }

    public String getValidVersionNumber(String versionNumStr) {
        return (versionNumStr == null || versionNumStr.trim().isEmpty()) ? "1" : versionNumStr.trim();
    }

    public boolean isMostRecentVersion(
        String versionType,
        String currentVersionStr,
        Map<String, Object> existingData
    ) {
        String key = getCacheKeyForVersionType(versionType);
        String cachedVersionStr = (String) existingData.get(key);
        return cachedVersionStr == null || compareVersionStrings(currentVersionStr, cachedVersionStr) >= 0;
    }

    private String getCacheKeyForVersionType(String versionType) {
        return Constants.VALID_ORIG_TYPES.contains(versionType.toUpperCase())
            ? "origVersionNumber"
            : "copyVersionNumber";
    }

    private int compareVersionStrings(String v1, String v2) {
        if (v1 == null) {
            v1 = "0";
        }
        if (v2 == null) {
            v2 = "0";
        }

        String[] v1Parts = v1.split("\\.");
        String[] v2Parts = v2.split("\\.");

        int length = Math.max(v1Parts.length, v2Parts.length);
        for (int i = 0; i < length; i++) {
            int v1Part = (i < v1Parts.length) ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = (i < v2Parts.length) ? Integer.parseInt(v2Parts[i]) : 0;

            if (v1Part < v2Part) {
                return -1;
            }
            
            if (v1Part > v2Part) {
                return 1;
            }
        }
        return 0;
    }
}