package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
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
    private LoggingService loggingService;
    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final InMemoryCacheService cacheService;

    @Autowired
    public RecordingMetadataProcessor(
        LoggingService loggingService,
        DataExtractionService extractionService,
        DataTransformationService transformationService,
        InMemoryCacheService cacheService
    ) {
        this.loggingService = loggingService;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.cacheService = cacheService;
    }

    /**
     * Processes a list of CSVArchiveListData items and caches the metadata.
     *
     * @param archiveItem List of CSVArchiveListData to process.
     */
    public void processRecording(CSVArchiveListData archiveItem) {
        try {
            ServiceResult<?> extracted = extractionService.process(archiveItem);
            if (extracted.getErrorMessage() != null) {
                return;
            }

            if (extracted.isTest()) {        
                return;  
            }

            ExtractedMetadata extractedData = (ExtractedMetadata) extracted.getData();
            ServiceResult<ProcessedRecording> result = transformationService.transformData(extractedData);
            ProcessedRecording cleansedData = (ProcessedRecording) result.getData();
            if (cleansedData == null) {
                ServiceResultUtil.failure("Data not transformed successfully", "Missing data");
                return;
            }

            String key = RecordingUtils.buildMetadataPreprocessKey(
                cleansedData.getUrn(),
                cleansedData.getDefendantLastName(),
                cleansedData.getWitnessFirstName()
            );

            Map<String, Object> existingMetadata = cacheService.getHashAll(key);
            if (existingMetadata == null) {
                existingMetadata = new HashMap<>();
            }

            Map<String, Object> updatedMetadata = RecordingUtils.updateVersionMetadata(
                cleansedData.getRecordingVersion(),
                cleansedData.getRecordingVersionNumberStr(),
                archiveItem.getArchiveName(),
                existingMetadata
            );

            cacheService.saveHashAll(key, updatedMetadata);
        } catch (Exception e) {
            ServiceResultUtil.failure(e.getMessage(), "Error");
        }
    }
}
