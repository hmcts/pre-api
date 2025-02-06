package uk.gov.hmcts.reform.preapi.util.batch;

import java.util.Map;
import java.util.logging.Logger;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;


@UtilityClass
public final class RecordingUtils {

    /**
     * Compares two version strings (e.g., "1.2" vs "1.10").
     * @return -1 if v1 < v2, 0 if they are equal, 1 if v1 > v2
     */
    public static int compareVersionStrings(String v1, String v2) {
        if (v1 == null) v1 = "0";
        if (v2 == null) v2 = "0";

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

    /**
     * Determine the version number for a given recording version string.
     * @param recordingVersion can be "ORIG" or any other string (treated as COPY)
     * @return 1 for "ORIG", otherwise 2
     */
    public int determineRecordingVersionNumber(String recordingVersion) {
        return "ORIG".equalsIgnoreCase(recordingVersion) ? 1 : 2;
    }

     /**
     * Returns "1" if the input is null or empty, otherwise returns the input.
     */
    public String getCurrentVersionNumber(String versionNumStr) {
        return (versionNumStr == null || versionNumStr.isEmpty()) ? "1" : versionNumStr;
    }

    /**
     * Evaluates whether the current version is the most recent compared to existing data,
     * depending on whether the version type is "ORIG" or "COPY".
     */
    public boolean evaluateRecency(
        String versionType, 
        String currentVersionNumber, 
        Map<String, String> existingData
    ) {
        switch (versionType.toUpperCase()) {
            case "ORIG":
                return isMostRecent(currentVersionNumber, "origVersionNumber",existingData);

            case "COPY":
                return isMostRecent(currentVersionNumber,"copyVersionNumber", existingData);

            default:
                Logger.getAnonymousLogger().warning("Unsupported version type: " + versionType);
                return false;
        }
    }

    /**
     * Returns true if the current version is greater than or equal to the stored version
     * (based on the key in existingData). If there is no stored version, true is returned.
     */
    private boolean isMostRecent(String currentVersionNumber, 
                                    String existingVersionKey,
                                    Map<String, String> existingData) {
        String storedVersion = existingData.get(existingVersionKey);
        return storedVersion == null
            || RecordingUtils.compareVersionStrings(currentVersionNumber, storedVersion) >= 0;
    }

    /**
     * Checks if a {@link CleansedData} item is labeled as an original version ("ORIG").
     */
    public boolean isOriginalVersion(CleansedData cleansedItem) {
        return "ORIG".equalsIgnoreCase(cleansedItem.getRecordingVersion());
    }

}
