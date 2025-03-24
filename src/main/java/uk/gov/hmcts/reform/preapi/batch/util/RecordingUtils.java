package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@UtilityClass
public final class RecordingUtils {
    private static final String RECORDING_METADATA_KEY = "vf:pre-process:%s-%s-%s";

    private static final String KEY_ORIG_VERSION_NUMBER = "origVersionNumber";
    private static final String KEY_COPY_VERSION_NUMBER = "copyVersionNumber";
    private static final String KEY_ORIG_ARCHIVE_NAME = "origVersionArchiveName";
    private static final String KEY_COPY_ARCHIVE_NAME = "copyVersionArchiveName";

    @Getter
    @AllArgsConstructor
    public static class VersionDetails {
        private final String versionType;
        private final String versionNumberStr;
        private final int versionNumber;
        private final boolean isMostRecent;

        @Override
        public String toString() {
            return String.format(
        "VersionDetails[type=%s, number=%s, versionNum=%d, isMostRecent=%b]", 
                versionType, versionNumberStr, versionNumber, isMostRecent);
        }
    }

    public VersionDetails processVersioning(
        String recordingVersion, 
        String versionNumberStr, 
        String urn, 
        String defendant, 
        String witness,
        Map<String, Object> existingCacheData
    ) {
        Map<String, Object> dataMap = existingCacheData != null ? existingCacheData : Collections.emptyMap();

        String versionType = Optional.ofNullable(recordingVersion)
                                     .map(String::toUpperCase)
                                     .filter(Constants.VALID_VERSION_TYPES::contains)
                                    .orElseThrow(() -> new IllegalArgumentException(
                                        "Invalid recording version: " + recordingVersion
                                    ));
                                     
        String validVersionNumber = getValidVersionNumber(versionNumberStr);
        int versionNumber = getRecordingVersionNumber(versionType);
        boolean isMostRecent = isMostRecentVersion(versionType, validVersionNumber, dataMap);

        return new VersionDetails(versionType, validVersionNumber, versionNumber, isMostRecent);
    }

    public int getRecordingVersionNumber(String recordingVersion) {
        return Constants.VALID_ORIG_TYPES.contains(recordingVersion.toUpperCase()) ? 1 : 2;
    }

    public String getValidVersionNumber(String versionNumStr) {
        return (versionNumStr == null || versionNumStr.trim().isEmpty()) ? "1" : versionNumStr;
    }

    public static boolean isMostRecentVersion(
        String versionType, 
        String currentVersion, 
        Map<String, Object> existingData
    ) {
        String key = Constants.VALID_ORIG_TYPES.contains(versionType.toUpperCase()) 
            ? KEY_ORIG_VERSION_NUMBER 
            : KEY_COPY_VERSION_NUMBER;

        String storedVersion = (String) existingData.get(key);
        return storedVersion == null || compareVersionStrings(currentVersion, storedVersion) >= 0;
    }
    

    public String buildMetadataPreprocessKey(String urn, String defendant, String witness) {
        return String.format(RECORDING_METADATA_KEY, urn, defendant, witness);
    }

    private static int compareVersionStrings(String v1, String v2) {
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

    public Map<String, Object> updateVersionMetadata(
        String versionType,
        String versionNumber,
        String archiveName,
        Map<String, Object> existingMetadata
    ) {
        Map<String, Object> updatedMetadata = new HashMap<>(existingMetadata);
        String validVersionNumber = getValidVersionNumber(versionNumber);

        if (Constants.VALID_ORIG_TYPES.contains(versionType.toUpperCase())) {
            String existingVersion = (String) existingMetadata.get(KEY_ORIG_VERSION_NUMBER);
            if (existingVersion == null || compareVersionStrings(validVersionNumber, existingVersion) > 0) {
                updatedMetadata.put(KEY_ORIG_ARCHIVE_NAME, archiveName);
                updatedMetadata.put(KEY_ORIG_VERSION_NUMBER, validVersionNumber);
            }
        } else if (Constants.VALID_COPY_TYPES.contains(versionType.toUpperCase())) {
            String existingVersion = (String) existingMetadata.get(KEY_COPY_VERSION_NUMBER);
            if (existingVersion == null || compareVersionStrings(validVersionNumber, existingVersion) > 0) {
                updatedMetadata.put(KEY_COPY_ARCHIVE_NAME, archiveName);
                updatedMetadata.put(KEY_COPY_VERSION_NUMBER, validVersionNumber);
            }
        }

        return updatedMetadata;
    }

}
