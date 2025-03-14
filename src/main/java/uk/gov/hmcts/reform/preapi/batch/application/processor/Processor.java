package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.RedisService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.validation.DataValidationService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

/**
 * Processes various CSV data types and transforms them into MigratedItemGroup for further processing.
 */
@Component
public class Processor implements ItemProcessor<Object, MigratedItemGroup> {
    private final RedisService redisService;
    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final MigrationGroupBuilderService migrationService;
    private LoggingService loggingService;

    @Autowired
    public Processor(
        RedisService redisService,
        DataExtractionService extractionService,
        DataTransformationService transformationService,
        DataValidationService validationService,
        ReferenceDataProcessor referenceDataProcessor,
        MigrationGroupBuilderService migrationService,
        MigrationTrackerService migrationTrackerService,
        LoggingService loggingService
    ) {
        this.redisService = redisService;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.referenceDataProcessor = referenceDataProcessor;
        this.migrationService = migrationService;
        this.migrationTrackerService = migrationTrackerService;
        this.loggingService = loggingService;
    }

    // =========================
    // Main Processor Logic
    // =========================
    @Override
    public MigratedItemGroup process(Object item) throws Exception {
        if (item == null) {
            loggingService.logWarning("Received null item to process");
            return null;
        }

        if (item instanceof CSVArchiveListData) {
            return processArchiveItem((CSVArchiveListData) item);
        } else if (item instanceof CSVSitesData || item instanceof CSVChannelData) {
            referenceDataProcessor.process(item);
            return null;
        } else {
            loggingService.logError("Unsuported item type: %s", item.getClass().getName());
        }
        return null;
    }

    private MigratedItemGroup processArchiveItem(CSVArchiveListData archiveItem) {
        try {
            loggingService.logDebug("===============================================");
            loggingService.logDebug("Processor started for item: %s", archiveItem);

            // Extraction
            ExtractedMetadata extractedData = extractData(archiveItem);
            if (extractedData == null) {
                return null;
            }

            // Transformation
            CleansedData cleansedData = transformData(archiveItem, extractedData);
            if (cleansedData == null) {
                return null;
            }
            
            // Check if already migrated
            if (isMigrated(cleansedData, archiveItem)) {
                return null;
            }

            // Validation
            if (!isValidated(cleansedData, archiveItem)) {
                return null;
            }

            loggingService.incrementProgress();
            return migrationService.createMigratedItemGroup(archiveItem, cleansedData);

        } catch (Exception e) {
            loggingService.logError("Error processing archive %s: %s", archiveItem.getArchiveName(), e.getMessage(), e);
            return handleError(archiveItem, "Failed to create migrated item group: " + e.getMessage(), "Error");
        }

    }


    // =========================
    // Extraction, Transformation and Validation
    // =========================
    private ExtractedMetadata extractData(CSVArchiveListData archiveItem) {
        ServiceResult<?> extractionResult = extractionService.process(archiveItem);

        // Handle test items
        if (extractionResult.isTest()) {
            TestItem testItem = extractionResult.getTestItem();
            handleTest(testItem);
            return null; 
        }

        // Handle extraction errors
        if (!extractionResult.isSuccess()) {
            handleError(archiveItem, extractionResult.getErrorMessage(), extractionResult.getCategory());
            return null;  
        }

        return (ExtractedMetadata) extractionResult.getData();
    }


    private CleansedData transformData(CSVArchiveListData archiveItem, ExtractedMetadata extractedData) {
        ServiceResult<CleansedData> result = transformationService.transformData(archiveItem, extractedData);
        if (checkForError(result, archiveItem)) {
            loggingService.logError("Failed to transform archive: %s", archiveItem.getSanitizedArchiveName());
            return null;
        }

        loggingService.logDebug("Transformed data: %s", result.getData());
        return result.getData();
    }

    private boolean isValidated(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        ServiceResult<CleansedData> result = validationService.validateCleansedData(cleansedData, archiveItem);
        if (checkForError(result, archiveItem)) {
            return false;
        }

        loggingService.logDebug("All validation rules passed");
        return true;
    }

    private boolean isMigrated(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        boolean alreadyMigrated = redisService.checkHashKeyExists("vf:case:", cleansedData.getCaseReference());
        if (alreadyMigrated) {
            handleError(archiveItem, "Already migrated: " + cleansedData.getCaseReference(), "Migrated");
            return true;
        }
        return false;
    }

    //======================
    // Helper Methods
    //======================
    private <T> boolean checkForError(ServiceResult<T> result, CSVArchiveListData archiveItem) {
        String errorMessage = (String) result.getErrorMessage();
        String category = (String) result.getCategory();

        if (errorMessage != null) {
            handleError(archiveItem, errorMessage, category);
            return true;
        }
        return false;
    }

    private MigratedItemGroup handleError(CSVArchiveListData archiveItem, String message, String category) {
        migrationTrackerService.addFailedItem(new FailedItem(archiveItem, message, category));
        // loggingService.logDebug("Error processing item [%s]: %s", category, message);
        return null;
    }

    private MigratedItemGroup handleTest(TestItem testItem) {
        migrationTrackerService.addTestItem(testItem);
        // loggingService.logDebug("Skipping test item: %s", testItem.getArchiveItem().getArchiveName());
        return null;
    }

    
}

