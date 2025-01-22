package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Processes recording metadata from CSVArchiveListData and caches it in Redis.
 */
@Component
public class RecordingMetadataProcessor {

    private static final String REDIS_KEY_FORMAT = "metadataPreprocess:%s-%s-%s";
    private final DataTransformationService transformationService;
    private final RedisService redisService;

    @Autowired
    public RecordingMetadataProcessor(DataTransformationService transformationService, RedisService redisService) {
        this.transformationService = transformationService;
        this.redisService = redisService;
    }

    // =========================
    // Main Processing Logic
    // =========================

    /**
     * Processes a list of CSVArchiveListData items and caches the metadata in Redis.
     * @param archiveItem List of CSVArchiveListData to process.
     */
    public void processRecording(CSVArchiveListData archiveItem) {
        try {
            // Transform the archive item into cleansed data
            Map<String, Object> transformationResult = transformationService.transformArchiveListData(
                archiveItem, redisService.getHashAll("sites_data", String.class, String.class), new HashMap<>()
            );

            CleansedData cleansedData = (CleansedData) transformationResult.get("cleansedData");
            String key = buildRedisKey(cleansedData);
            
            // Get existing metadata from Redis
            Map<String, String> existingMetadata = redisService.getHashAll(key, String.class, String.class);
            if (existingMetadata == null) {
                existingMetadata = new HashMap<>();
            }

            // Extract version information
            String versionType = cleansedData.getRecordingVersion();
            String versionNumberStr = cleansedData.getRecordingVersionNumberStr();
            versionNumberStr = (versionNumberStr == null || versionNumberStr.isEmpty()) ? "1" : versionNumberStr;
            String archiveName = archiveItem.getArchiveName();
            
            // Update metadata based on version type
            if ("ORIG".equalsIgnoreCase(versionType)) {
                String existingOrigVersionStr = existingMetadata.get("origVersionNumber");
                if (existingOrigVersionStr == null || compareVersionNumbers(versionNumberStr, existingOrigVersionStr) > 0) {
                    existingMetadata.put("origVersionArchiveName", archiveName);
                    existingMetadata.put("origVersionNumber", versionNumberStr);
                }
            } else if ("COPY".equalsIgnoreCase(versionType)) {
                String existingCopyVersionStr = existingMetadata.get("copyVersionNumber");
                if (existingCopyVersionStr == null || compareVersionNumbers(versionNumberStr, existingCopyVersionStr) > 0) {
                    existingMetadata.put("copyVersionArchiveName", archiveName);
                    existingMetadata.put("copyVersionNumber", versionNumberStr);
                }
            }

            // Save updated metadata back to Redis
            redisService.saveHashAll(key, existingMetadata);

        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Failed to process archive item: " 
                + archiveItem.getArchiveName() + ". Reason: " + e.getMessage()); 
        }
    }

    // =========================
    // Helper Methods
    // =========================

    /**
     * Compares two version numbers.
     * @param v1 The first version number.
     * @param v2 The second version number.
     * @return A negative integer, zero, or a positive integer if v1 is less than, equal to, or greater than v2.
     */
    public static int compareVersionNumbers(String v1, String v2) {
        String[] v1Parts = v1.split("\\.");
        String[] v2Parts = v2.split("\\.");

        int length = Math.max(v1Parts.length, v2Parts.length);
        for (int i = 0; i < length; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

            if (v1Part < v2Part) return -1;
            if (v1Part > v2Part) return 1;
        }
        return 0;
    }

    /**
     * Builds a Redis key based on cleansed data.
     * @param cleansedData The cleansed data containing URN, defendant, and witness information.
     * @return A formatted Redis key.
     */
    private String buildRedisKey(CleansedData cleansedData) {
        String urn = cleansedData.getUrn();
        String defendant = cleansedData.getDefendantLastName();
        String witness = cleansedData.getWitnessFirstName();
        
        return String.format(REDIS_KEY_FORMAT, urn, defendant, witness);
    }
   
}
