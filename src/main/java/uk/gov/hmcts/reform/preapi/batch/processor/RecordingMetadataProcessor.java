package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.services.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.services.RedisService;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes recording metadata from CSVArchiveListData and caches it in Redis.
 */
@Component
public class RecordingMetadataProcessor {
    private final DataTransformationService transformationService;
    private final RedisService redisService;

    @Autowired
    public RecordingMetadataProcessor(DataTransformationService transformationService, RedisService redisService) {
        this.transformationService = transformationService;
        this.redisService = redisService;
    }

    /**
     * Processes a list of CSVArchiveListData items and caches the metadata in Redis.
     * @param archiveItem List of CSVArchiveListData to process.
     */
    public void processRecording(CSVArchiveListData archiveItem) {
        try {
            ServiceResult<CleansedData> result =  transformationService.transformData(archiveItem);
            CleansedData cleansedData = (CleansedData) result.getData();
            if (cleansedData == null) {
                ServiceResultUtil.createFailureReponse("Data not transformed successfully");
                return;
            }

            String redisKey = RecordingUtils.buildMetadataPreprocessKey(
                cleansedData.getUrn(),
                cleansedData.getDefendantLastName(),
                cleansedData.getWitnessFirstName()
            );
            
            Map<String, String> existingMetadata = redisService.getHashAll(redisKey, String.class, String.class);
            if (existingMetadata == null) {
                existingMetadata = new HashMap<>();
            }

            Map<String, String> updatedMetadata = RecordingUtils.updateVersionMetadata(
                cleansedData.getRecordingVersion(),
                cleansedData.getRecordingVersionNumberStr(),
                archiveItem.getArchiveName(),
                existingMetadata
            );

            redisService.saveHashAll(redisKey, updatedMetadata);

        } catch (Exception e) {
            ServiceResultUtil.createFailureReponse(e.getMessage());
        }
    }
}
