package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.CleansedData;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.services.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.services.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.services.DataValidationService;
import uk.gov.hmcts.reform.preapi.batch.services.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.batch.services.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.services.RedisService;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Processes various CSV data types and transforms them into MigratedItemGroup for further processing.
 */
@Component
public class Processor implements ItemProcessor<Object, MigratedItemGroup> {
    private static final Logger logger = LoggerFactory.getLogger(Processor.class);

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

            if (checkMigrated(cleansedData, archiveItem)) {
                return null;
            }
            
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

    private boolean checkMigrated(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        boolean alreadyMigrated = redisService.checkHashKeyExists("vf:case:", cleansedData.getCaseReference());
        if (alreadyMigrated) {
            handleError(archiveItem, "Already migrated: " + cleansedData.getCaseReference());
            return true;
        }
        return false;
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

