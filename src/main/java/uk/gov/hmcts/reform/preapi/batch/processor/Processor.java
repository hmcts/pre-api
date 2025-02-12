package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.entities.batch.FailedItem;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.entities.batch.TransformationResult;
import uk.gov.hmcts.reform.preapi.services.batch.DataExtractionService;
import uk.gov.hmcts.reform.preapi.services.batch.DataTransformationService;
import uk.gov.hmcts.reform.preapi.services.batch.DataValidationService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

import java.util.HashMap;
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

    private final Map<String, CreateRecordingDTO> origRecordingsMap = new HashMap<>();

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
        // 1. Transform Data
        CleansedData cleansedData;
        
        try {
            cleansedData = transformData(archiveItem);
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(
                new FailedItem(archiveItem, "Transformation error: " + e.getMessage())
            );
            return null;
        }
       
        // 2. Check if already migrated
        boolean alreadyMigrated = redisService.hashKeyExists("vf:case:", cleansedData.getCaseReference());
        if (alreadyMigrated) {
            migrationTrackerService.addFailedItem(new FailedItem(
                archiveItem, "Already migrated: " + cleansedData.getCaseReference()));
            return null;
        }

        // 3. Validate Transformed Data
        boolean validated = validateData(cleansedData, archiveItem);
        if (!validated) {
            return null;
        } 

        // 4. Extract Pattern from Archive
        String pattern;
        pattern = extractPattern(archiveItem);

        // 5. Create Migrated Item Group
        try {
            return migrationService.createMigratedItemGroup(pattern,archiveItem,cleansedData);
            // return createMigratedItemGroup(pattern, archiveItem, cleansedData);
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(
                new FailedItem(archiveItem, "Failed to create migrated item group: " + e.getMessage())
            );
            return null;
        }
    }

    
    // =========================
    // Transformation and Validation
    // =========================
    private CleansedData transformData(CSVArchiveListData archiveItem) {
        TransformationResult result = transformationService.transformData(archiveItem);
        if (checkForError(result, archiveItem)) {
            return null;
        }
        return (CleansedData) result.getCleansedData();
    }

    private boolean validateData(CleansedData cleansedData, CSVArchiveListData archiveItem) {
        TransformationResult result = validationService.validateCleansedData(cleansedData, archiveItem);
        if (checkForError(result, archiveItem)) {
            return false;
        }
        return true;
    }
    
    //======================
    // Helper Methods
    //======================
    private boolean checkForError(TransformationResult result, CSVArchiveListData archiveItem) {
        String errorMessage = (String) result.getErrorMessage();
        if (errorMessage != null) {
            migrationTrackerService.addFailedItem(new FailedItem(archiveItem, errorMessage));
            return true;
        }
        return false;
    }

    private String extractPattern(CSVArchiveListData archiveItem) {
        try {
            Map.Entry<String, Matcher> patternMatch = extractionService.matchPattern(archiveItem);
            return patternMatch != null ? patternMatch.getKey() : null; 
        } catch (Exception e) {
            migrationTrackerService.addFailedItem(new FailedItem(archiveItem, e.getMessage()));
            return null;
        }
    }
}

