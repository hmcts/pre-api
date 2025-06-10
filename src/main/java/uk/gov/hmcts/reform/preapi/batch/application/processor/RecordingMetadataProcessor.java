package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
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

    @Autowired
    public RecordingMetadataProcessor(
        final DataExtractionService extractionService,
        final DataTransformationService transformationService,
        final InMemoryCacheService cacheService
    ) {
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
            if (extracted.getErrorMessage() != null || extracted.isTest()) {
                return;
            }

            ExtractedMetadata extractedData = (ExtractedMetadata) extracted.getData();
            ServiceResult<ProcessedRecording> result = transformationService.transformData(extractedData);
            if (result.getData() == null) {
                ServiceResultUtil.failure("Data not transformed successfully", "Missing data");
                return;
            }

            ProcessedRecording cleansedData = result.getData();
            String cacheKey = cacheService.generateCacheKey(
                "recording",
                cleansedData.getCaseReference(),
                cleansedData.getDefendantLastName(),
                cleansedData.getWitnessFirstName()
            );

            String versionType = cleansedData.getExtractedRecordingVersion().toUpperCase(); // ORIG or COPY
            String versionNumber = cleansedData.getExtractedRecordingVersionNumberStr();
            String fileName = archiveItem.getArchiveName();

            Map<String, Object> metadata = cacheService.getHashAll(cacheKey);
            if (metadata == null) {
                metadata = new HashMap<>();
            } 

            switch (versionType) {
                case "ORIG" -> {
                    String key = "archiveName:orig:" + versionNumber;
                    metadata.put(key, fileName);
                }
                case "COPY" -> {
                    String copyKey = "archiveName:copy:" + versionNumber;
                    metadata.put(copyKey, fileName);

                    String origKey = "archiveName:orig:" + versionNumber;
                    String origFile = (String) metadata.get(origKey);
                    if (origFile != null) {
                        String copyVersionKey = "copyVersionArchiveName:" + versionNumber;
                        metadata.put(copyVersionKey, origFile);
                    } 
                }
                default -> throw new IllegalArgumentException("Unexpected version type: " + versionType);
            }

            cacheService.saveHashAll(cacheKey, metadata);

        } catch (Exception e) {
            ServiceResultUtil.failure(e.getMessage(), "Error processing recording metadata");
        }
    }
}
