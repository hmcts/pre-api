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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        InMemoryCacheService cacheService,
        DataExtractionService extractionService,
        DataTransformationService transformationService,
        DataValidationService validationService,
        ReferenceDataProcessor referenceDataProcessor,
        MigrationGroupBuilderService migrationService,
        MigrationTrackerService migrationTrackerService,
        LoggingService loggingService
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
        } else if (item instanceof CSVExemptionListData csvExemptionListData) {
            return processExemptionItem(csvExemptionListData);
        } else if (item instanceof CSVSitesData || item instanceof CSVChannelData) {
            referenceDataProcessor.process(item);
            return null;
        } else {
            loggingService.logError("Unsuported item type: %s", item.getClass().getName());
        }
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
            return migrationService.createMigratedItemGroup(extractedData, cleansedData);

        } catch (Exception e) {
            loggingService.logError(
                "Error processing archive %s: %s",
                extractedData.getArchiveName(),
                e.getMessage(),
                e
            );
            return handleError(extractedData, "Failed to create migrated item group: " + e.getMessage(), "Error");
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

    private boolean isMigrated(ProcessedRecording cleansedData, CSVArchiveListData archiveItem) {
        boolean alreadyMigrated = cacheService.checkHashKeyExists("vf:case:", cleansedData.getCaseReference());
        if (alreadyMigrated) {
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

        LocalDateTime parsedCreateTime = parseDateTime(exemptionItem.getCreateTime());

        return new ExtractedMetadata(
            exemptionItem.getCourtReference(),
            exemptionItem.getUrn(),
            exemptionItem.getExhibitReference(),
            exemptionItem.getDefendantName(),
            exemptionItem.getWitnessName(),
            exemptionItem.getRecordingVersion(),
            String.valueOf(exemptionItem.getRecordingVersionNumber()),
            exemptionItem.getFileExtension(),
            parsedCreateTime,
            exemptionItem.getDuration(),
            exemptionItem.getFileName(),
            exemptionItem.getFileSize(),
            exemptionItem.getArchiveName()
        );
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            loggingService.logWarning("Empty or NULL date received for parsing.");
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (Exception e) {
            loggingService.logError("Failed to parse date: " + dateTimeStr, e);
            return null;
        }
    }

    // =========================
    // Notifications
    // =========================
    private void checkAndCreateNotifyItem(ExtractedMetadata extractedData) {
        if (extractedData.getDefendantLastName().contains("-")) {
            loggingService.logDebug("Double-barrelled defendant detected: %s", extractedData.getDefendantLastName());
            migrationTrackerService.addNotifyItem(new NotifyItem("Double-barelled defendant",extractedData));
        }

        if (extractedData.getWitnessFirstName().contains("-")) {
            loggingService.logDebug("Double-barrelled witness detected: %s", extractedData.getDefendantLastName());
            migrationTrackerService.addNotifyItem(new NotifyItem("Double-barelled witness",extractedData));
        }
    }

}

