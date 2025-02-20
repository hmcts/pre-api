package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.ServiceResult;
import uk.gov.hmcts.reform.preapi.services.batch.DataExtractionService;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.DataValidationService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Processes various CSV data types and transforms them into MigratedItemGroup for further processing.
 */
@Component
public class Processor implements ItemProcessor<Object, MigratedItemGroup> {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    private final DataExtractionService extractionService;
    private final RedisService redisService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final MigrationGroupBuilderService migrationService;

    @Autowired
    public Processor(
        RedisService redisService,
        DataExtractionService extractionService,
        DataTransformationService transformationService,
        DataValidationService validationService,
        ReferenceDataProcessor referenceDataProcessor,
        MigrationGroupBuilderService migrationService,
        MigrationTrackerService migrationTrackerService
    ) {
        this.redisService = redisService;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.referenceDataProcessor = referenceDataProcessor;
        this.migrationService = migrationService;
        this.migrationTrackerService = migrationTrackerService;
    }

    // =========================
    // Main Processor Logic
    // =========================
    @Override
    public MigratedItemGroup process(Object item) throws Exception {
        if (item instanceof CSVArchiveListData) {
            return process((CSVArchiveListData) item);
        } else if (item instanceof CSVSitesData || item instanceof CSVChannelData) {
            referenceDataProcessor.process(item);
            return null;
        } else {
            logger.error("Unsuported item type: {}", item.getClass().getName());
        }
        return null;
    }

    private MigratedItemGroup process(CSVArchiveListData archiveItem) {
        try {
            
            CleansedData cleansedData = transformData(archiveItem);
            if (cleansedData == null) {
                return null;
            }
            checkMigrated(cleansedData, archiveItem);

            if (!validateData(cleansedData, archiveItem)) {
                return null;
            } 

            String pattern = extractPattern(archiveItem);
            return migrationService.createMigratedItemGroup(pattern, archiveItem, cleansedData);
        
        } catch (Exception e) {
            return handleError(archiveItem, "Failed to create migrated item group: " + e.getMessage());
        }
    }

    
    // =========================
    // Transformation and Validation
    // =========================
    private CleansedData transformData(CSVArchiveListData archiveItem) {
        ServiceResult<CleansedData> result = transformationService.transformData(archiveItem);
        if (checkForError(result, archiveItem)) {
            return null;
        }
        return (CleansedData) result.getData();
    }

    private boolean validateData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        ServiceResult<CleansedData> result = validationService.validateCleansedData(cleansedData, archiveItem);
        if (checkForError(result, archiveItem)) {
            return false;
        }
        return true;
    }

    private void checkMigrated(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        boolean alreadyMigrated = redisService.hashKeyExists("vf:case:", cleansedData.getCaseReference());
        if (alreadyMigrated) {
            handleError(archiveItem, "Already migrated: " + cleansedData.getCaseReference());
        }
       
    }

    //======================
    // Helper Methods
    //======================
    private boolean checkForError(ServiceResult<CleansedData> result, CSVArchiveListData archiveItem) {
        String errorMessage = (String) result.getErrorMessage();
        if (errorMessage != null) {
            handleError(archiveItem, errorMessage);
            return true;
        }
        return false;
    }

    private String extractPattern(CSVArchiveListData archiveItem) {
        try {
            Map.Entry<String, Matcher> patternMatch = extractionService.matchPattern(archiveItem);
            return patternMatch != null ? patternMatch.getKey() : null; 
        } catch (Exception e) {
            handleError(archiveItem, e.getMessage());
            return null;
        }
    }

    private MigratedItemGroup handleError(CSVArchiveListData archiveItem, String message) {
        migrationTrackerService.addFailedItem(new FailedItem(archiveItem, message));
        return null;
    }
}

