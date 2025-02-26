package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;


@UtilityClass
public final class RecordingUtils {
    private static final String REDIS_RECORDING_METADATA_KEY = "vf:pre-process:%s-%s-%s";

    @Getter
    @AllArgsConstructor
    public static class VersionDetails {
        private final String versionType;
        private final String versionNumberStr;
        private final int versionNumber;
        private final boolean isMostRecent;
    }

    public VersionDetails processVersioning(
        String recordingVersion, 
        String versionNumberStr, 
        String urn, 
        String defendant, 
        String witness,
        Map<String, String> existingRedisData
    ) {
        String versionType = recordingVersion;
        String validVersionNumber = getValidVersionNumber(versionNumberStr);
        int versionNumber = getRecordingVersionNumber(versionType);
        boolean isMostRecent = isMostRecentVersion(versionType, validVersionNumber, existingRedisData);

        return new VersionDetails(versionType, validVersionNumber, versionNumber, isMostRecent);
    }

    public int getRecordingVersionNumber(String recordingVersion) {
        return "ORIG".equalsIgnoreCase(recordingVersion) ? 1 : 2;
    }

    public String getValidVersionNumber(String versionNumStr) {
        return (versionNumStr == null || versionNumStr.isEmpty()) ? "1" : versionNumStr;
    }

    public static boolean isMostRecentVersion(
        String versionType, 
        String currentVersion, 
        Map<String, String> existingData
    ) {
        String key = "ORIG".equalsIgnoreCase(versionType) ? "origVersionNumber" : "copyVersionNumber";
        String storedVersion = existingData.get(key);

        boolean isRecent = storedVersion == null || compareVersionStrings(currentVersion, storedVersion) >= 0;
        return isRecent;
    }
    

    public String buildMetadataPreprocessKey(String urn, String defendant, String witness) {
        return String.format(REDIS_RECORDING_METADATA_KEY, urn, defendant, witness);
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

    public Map<String, String> updateVersionMetadata(
        String versionType,
        String versionNumber,
        String archiveName,
        Map<String, String> existingMetadata
    ) {
        Map<String, String> updatedMetadata = new HashMap<>(existingMetadata);
        String validVersionNumber = getValidVersionNumber(versionNumber);

        if ("ORIG".equalsIgnoreCase(versionType)) {
            String existingVersion = existingMetadata.get("origVersionNumber");
            if (existingVersion == null || compareVersionStrings(validVersionNumber, existingVersion) > 0) {
                updatedMetadata.put("origVersionArchiveName", archiveName);
                updatedMetadata.put("origVersionNumber", validVersionNumber);
            }
        } else if ("COPY".equalsIgnoreCase(versionType)) {
            String existingVersion = existingMetadata.get("copyVersionNumber");
            if (existingVersion == null || compareVersionStrings(validVersionNumber, existingVersion) > 0) {
                updatedMetadata.put("copyVersionArchiveName", archiveName);
                updatedMetadata.put("copyVersionNumber", validVersionNumber);
            }
        }

        return updatedMetadata;
    }

}
