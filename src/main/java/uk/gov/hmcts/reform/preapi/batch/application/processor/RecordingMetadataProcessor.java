package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import java.util.Optional;

/**
 * Processes recording metadata from vf_migration_records with status PENDING.
 */
@Component
public class RecordingMetadataProcessor {
    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final MigrationRecordService migrationRecordService;
    private final LoggingService loggingService;

    @Autowired
    public RecordingMetadataProcessor(
        final DataExtractionService extractionService,
        final DataTransformationService transformationService,
        final MigrationRecordService migrationRecordService,
        final LoggingService loggingService
    ) {
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.migrationRecordService = migrationRecordService;
        this.loggingService = loggingService;
    }

    /**
     * Processes a list of CSVArchiveListData items and caches the metadata.
     *
     * @param archiveItem List of CSVArchiveListData to process.
     */
   
    public void processRecording(MigrationRecord archiveItem) {
        try {

            Optional<MigrationRecord> existingRecord = migrationRecordService.findByArchiveId(
                archiveItem.getArchiveId());
            if (existingRecord.isPresent() && existingRecord.get().getStatus() != VfMigrationStatus.PENDING) {
                return;
            }

            ServiceResult<?> extracted = extractionService.process(archiveItem);
            if (extracted.getErrorMessage() != null || extracted.isTest()) {
                return;
            }

            ExtractedMetadata extractedData = (ExtractedMetadata) extracted.getData();

            migrationRecordService.updateMetadataFields(archiveItem.getArchiveId(), extractedData);

            ServiceResult<ProcessedRecording> result = transformationService.transformData(extractedData);
            if (result.getData() == null) {
                ServiceResultUtil.failure("Data not transformed successfully", "Missing data");
                return;
            }
            
            ProcessedRecording cleansedData = result.getData();
            migrationRecordService.updateIsPreferred(
                cleansedData.getArchiveId(),
                cleansedData.isPreferred()
            );
        
            String origVersionStr = cleansedData.getOrigVersionNumberStr();

            String groupKey = MigrationRecordService.generateRecordingGroupKey(
                extractedData.getUrn(),
                extractedData.getExhibitReference(),
                extractedData.getWitnessFirstName(),
                extractedData.getDefendantLastName()
            );

            if ("COPY".equalsIgnoreCase(extractedData.getRecordingVersion())) {
                migrationRecordService.updateParentTempIdIfCopy(
                    archiveItem.getArchiveId(), 
                    groupKey,
                    origVersionStr
                );
            }

        } catch (Exception e) {
            ServiceResultUtil.failure(e.getMessage(), "Error processing recording metadata");
        }
    }

}

