package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes recording metadata from CSVArchiveListData and caches it.
 */
@Component
public class RecordingMetadataProcessor {
    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final InMemoryCacheService cacheService;
    private final LoggingService loggingService;

    @Autowired
    public RecordingMetadataProcessor(
        final DataExtractionService extractionService,
        final DataTransformationService transformationService,
        final InMemoryCacheService cacheService,
        final LoggingService loggingService
    ) {
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.cacheService = cacheService;
        this.loggingService = loggingService;
    }

    /**
     * Processes a list of CSVArchiveListData items and caches the metadata.
     *
     * @param archiveItem List of CSVArchiveListData to process.
     */
   
    public void processRecording(CSVArchiveListData archiveItem) {
        try {
            ServiceResult<?> extracted = extractionService.process(archiveItem);
            if (extracted.getErrorMessage() != null || extracted.isTest()) {
                return;
            }

            ExtractedMetadata extractedData = (ExtractedMetadata) extracted.getData();

            String origVersionStr =
                extractedData.getRecordingVersion().startsWith("COPY")
                    ? extractedData.getRecordingVersionNumber().split("\\.")[0] 
                    : extractedData.getRecordingVersionNumber().isEmpty() ? "1" 
                    : extractedData.getRecordingVersionNumber();

            String cacheKey = cacheService.generateEntityCacheKey(
                "recording",
                extractedData.createCaseReference(),
                extractedData.getDefendantLastName(),
                extractedData.getWitnessFirstName(),
                origVersionStr
            );
            
            ServiceResult<ProcessedRecording> result = transformationService.transformData(extractedData);
            if (result.getData() == null) {
                ServiceResultUtil.failure("Data not transformed successfully", "Missing data");
                return;
            }
            
            updateVersionCache(extractedData, cacheKey);
            ProcessedRecording cleansedData = result.getData();
        
            String fileName = archiveItem.getArchiveName();
            String extractedVersionType = cleansedData.getExtractedRecordingVersion().toUpperCase(); // ORIG or COPY
            String copyVersionStr = cleansedData.getCopyVersionNumberStr(); 

            Map<String, Object> metadata = cacheService.getHashAll(cacheKey);
            if (metadata == null) {
                metadata = new HashMap<>();
            }

            if ("ORIG".equals(extractedVersionType)) {
                String key = "archiveName:orig:" + origVersionStr;
                metadata.put(key, fileName);


            } else if ("COPY".equals(extractedVersionType)) {
                String fullVersionStr = origVersionStr + (copyVersionStr != null ? "." + copyVersionStr : "");
                String key = "archiveName:copy:" + fullVersionStr;
                metadata.put(key, fileName);

                String existingCopy = (String) metadata.get("copyVersionNumber");
                if (existingCopy == null || RecordingUtils.compareVersionStrings(fullVersionStr, existingCopy) > 0) {
                    metadata.put("copyVersionNumber", fullVersionStr);
                }

                String origKey = "archiveName:orig:" + origVersionStr;
                String origFile = (String) metadata.get(origKey);
                if (origFile != null) {
                    metadata.put("copyVersionArchiveName:" + fullVersionStr, origFile);
                }

                loggingService.logInfo("In Recording Metadata Processor: storing: %s", metadata);
            } else {
                throw new IllegalArgumentException("Unexpected version type: " + extractedVersionType);
            }
            
            cacheService.saveHashAll(cacheKey, metadata);

            
        } catch (Exception e) {
            ServiceResultUtil.failure(e.getMessage(), "Error processing recording metadata");
        }
    }

    private void updateVersionCache(ExtractedMetadata extracted, String cacheKey) {
        String versionType = extracted.getRecordingVersion().toUpperCase();
        String versionNumber = extracted.getRecordingVersionNumber(); // e.g., "1.2" or "2"

        Map<String, Object> metadata = cacheService.getHashAll(cacheKey);
        if (metadata == null) {
            metadata = new HashMap<>();
        } 

        String versionKey = Constants.VALID_ORIG_TYPES.contains(versionType)
            ? "origVersionNumber"
            : "copyVersionNumber";

        String existing = (String) metadata.get(versionKey);
        if (existing == null || RecordingUtils.compareVersionStrings(versionNumber, existing) > 0) {
            metadata.put(versionKey, versionNumber);
        }
        cacheService.saveHashAll(cacheKey, metadata);
    }


}

