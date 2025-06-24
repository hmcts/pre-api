package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.validation.DataValidationService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVExemptionListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.IArchiveData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes various CSV data types and transforms them into MigratedItemGroup for further processing.
 */
@Component
public class Processor implements ItemProcessor<Object, MigratedItemGroup> {
    private final InMemoryCacheService cacheService;
    private final DataExtractionService extractionService;
    private final DataTransformationService transformationService;
    private final DataValidationService validationService;
    private final MigrationTrackerService migrationTrackerService;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final MigrationGroupBuilderService migrationService;
    private final LoggingService loggingService;

    @Autowired
    public Processor(
        final InMemoryCacheService cacheService,
        final DataExtractionService extractionService,
        final DataTransformationService transformationService,
        final DataValidationService validationService,
        final ReferenceDataProcessor referenceDataProcessor,
        final MigrationGroupBuilderService migrationService,
        final MigrationTrackerService migrationTrackerService,
        final LoggingService loggingService
    ) {
        this.cacheService = cacheService;
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
        if (item instanceof CSVArchiveListData csvArchiveListData) {
            return processArchiveItem(csvArchiveListData);
        }
        if (item instanceof CSVExemptionListData csvExemptionListData) {
            return processExemptionItem(csvExemptionListData);
        }
        if (item instanceof CSVSitesData || item instanceof CSVChannelData) {
            referenceDataProcessor.process(item);
            return null;
        }

        loggingService.logError("Unsupported item type: %s", item.getClass().getName());
        return null;
    }

    private MigratedItemGroup processExemptionItem(CSVExemptionListData exemptionItem) {
        loggingService.logDebug("===============================================");
        loggingService.logDebug("Processing Exemption Item: %s", exemptionItem);

        ExtractedMetadata extractedData = convertToExtractedMetadata(exemptionItem);
        try {
            if (extractedData == null) {
                return null;
            }

            ProcessedRecording cleansedData = transformData(extractedData);
            if (cleansedData == null) {
                return null;
            }

            if (!isExemptionValidated(cleansedData, exemptionItem)) {
                return null;
            }

            loggingService.incrementProgress();           
            cacheService.dumpToFile();
            
            return migrationService.createMigratedItemGroup(extractedData, cleansedData);

        } catch (Exception e) {
            loggingService.logError(
                "Error processing archive %s: %s",
                extractedData.getArchiveName(),
                e.getMessage(),
                e
            );
            handleError(extractedData, "Failed to create migrated item group: " + e.getMessage(), "Error");
            return null;
        }
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
            ProcessedRecording cleansedData = transformData(extractedData);
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
            cacheService.dumpToFile();
         
            return migrationService.createMigratedItemGroup(extractedData, cleansedData);
        } catch (Exception e) {
            loggingService.logError("Error processing archive %s: %s", archiveItem.getArchiveName(), e.getMessage(), e);
            handleError(archiveItem, "Failed to create migrated item group: " + e.getMessage(), "Error");
            return null;
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

        ExtractedMetadata extractedData = (ExtractedMetadata) extractionResult.getData();
        checkAndCreateNotifyItem(extractedData);
        return extractedData;
    }

    private ProcessedRecording transformData(ExtractedMetadata extractedData) {
        ServiceResult<ProcessedRecording> result = transformationService.transformData(extractedData);
        if (checkForError(result, extractedData)) {
            loggingService.logError("Failed to transform archive: %s", extractedData.getSanitizedArchiveName());
            return null;
        }

        loggingService.logDebug("Transformed data: %s", result.getData());
        return result.getData();
    }

    private boolean isValidated(ProcessedRecording cleansedData, CSVArchiveListData archiveItem) {
        ServiceResult<ProcessedRecording> result = validationService.validateProcessedRecording(
            cleansedData,
            archiveItem
        );
        if (checkForError(result, archiveItem)) {
            return false;
        }

        loggingService.logDebug("All validation rules passed");
        return true;
    }

    private boolean isExemptionValidated(ProcessedRecording cleansedData, CSVExemptionListData exemptionItem) {
        ServiceResult<ProcessedRecording> result = validationService.validateExemptionRecording(
            cleansedData,
            exemptionItem.getArchiveName()
        );

        if (checkForError(result, exemptionItem)) {
            return false;
        }
    
        loggingService.logDebug("All validation rules passed");
        return true;
    }

  
    private boolean isMigrated(ProcessedRecording cleansedData, CSVArchiveListData archiveItem) {
        String key = cacheService.generateEntityCacheKey(
            "recording",
            cleansedData.getCaseReference(),
            cleansedData.getDefendantLastName(),
            cleansedData.getWitnessFirstName(),
            cleansedData.getOrigVersionNumberStr()
        );

        Map<String, Object> metadata = cacheService.getHashAll(key);
        String archiveName = archiveItem.getArchiveName();
        String versionStr = cleansedData.getExtractedRecordingVersion(); // e.g. ORIG1, COPY2
        int version = cleansedData.getRecordingVersionNumber();

        boolean isOrig = versionStr != null && versionStr.toUpperCase().startsWith("ORIG");
        boolean isCopy = versionStr != null && versionStr.toUpperCase().startsWith("COPY");

        String archiveKey = isOrig ? "origVersionArchiveName:" + version : "copyVersionArchiveName:" + version;

        if (metadata != null) {
            // boolean seen = Boolean.TRUE.equals(metadata.get(seenKey));
            String seenArchive = (String) metadata.get(archiveKey);

            // if (seen && archiveName.equalsIgnoreCase(seenArchive)) {
            if (archiveName.equalsIgnoreCase(seenArchive)) {
                handleError(archiveItem, "Duplicate recording already seen", "Duplicate");
                return true;
            }
        } else {
            metadata = new HashMap<>();
        }

        cacheService.saveHashAll(key, metadata);

        boolean alreadyMigrated = cacheService.getCase(cleansedData.getCaseReference()).isPresent();
        if (alreadyMigrated) {
            loggingService.logDebug("Case already migrated: %s", cleansedData.getCaseReference());
            handleError(archiveItem, "Already migrated: " + cleansedData.getCaseReference(), "Migrated");
            return true;
        }

        return false;
    }
    
    //======================
    // Helper Methods
    //======================
    
    private <T> boolean checkForError(ServiceResult<T> result, IArchiveData item) {
        String errorMessage = result.getErrorMessage();
        String category = result.getCategory();

        if (errorMessage != null) {
            handleError(item, errorMessage, category);
            return true;
        }
        return false;
    }

    private MigratedItemGroup handleError(IArchiveData item, String message, String category) {
        migrationTrackerService.addFailedItem(new FailedItem(item, message, category));
        return null;
    }

    private MigratedItemGroup handleTest(TestItem testItem) {
        migrationTrackerService.addTestItem(testItem);
        return null;
    }

    private ExtractedMetadata convertToExtractedMetadata(CSVExemptionListData exemptionItem) {
        if (exemptionItem == null) {
            loggingService.logWarning("Received NULL exemption item for conversion!");
            return null;
        }

        loggingService.logInfo("Converting Exemption Item to ExtractedMetadata: " + exemptionItem);
        return new ExtractedMetadata(
            exemptionItem.getCourtReference(),
            exemptionItem.getUrn(),
            exemptionItem.getExhibitReference(),
            exemptionItem.getDefendantName(),
            exemptionItem.getWitnessName(),
            exemptionItem.getRecordingVersion(),
            String.valueOf(exemptionItem.getRecordingVersionNumber()),
            exemptionItem.getFileExtension(),
            exemptionItem.getCreateTimeAsLocalDateTime(),
            exemptionItem.getDuration(),
            exemptionItem.getFileName(),
            exemptionItem.getFileSize(),
            exemptionItem.getArchiveName()
        );
    }

    // =========================
    // Notifications
    // =========================
    private void checkAndCreateNotifyItem(ExtractedMetadata extractedData) {
        String defendantLastName = extractedData.getDefendantLastName();
        String witnessFirstName = extractedData.getWitnessFirstName();

        // Double-barrelled name checks
        if (defendantLastName != null && defendantLastName.contains("-")) {
            migrationTrackerService.addNotifyItem(new NotifyItem("Double-barrelled name",extractedData));
        }

        if (witnessFirstName != null && witnessFirstName.contains("-")) {
            migrationTrackerService.addNotifyItem(new NotifyItem("Double-barrelled name",extractedData));
        }

        String urn = extractedData.getUrn();
        String exhibitRef = extractedData.getExhibitReference();

        // case ref checks
        if (urn == null || urn.isEmpty()) {
            migrationTrackerService.addNotifyItem(new NotifyItem("Missing URN",extractedData));
        } else if (urn.length() < 11) {
            migrationTrackerService.addNotifyItem(new NotifyItem("Invalid URN length", extractedData));
        }

        if (exhibitRef == null || exhibitRef.isEmpty()) {
            migrationTrackerService.addNotifyItem(new NotifyItem("Missing Exhibit Ref", extractedData));
        } else if (exhibitRef.length() < 9) {
            migrationTrackerService.addNotifyItem(new NotifyItem("Invalid Exhibit length", extractedData));
        }
    }

}

