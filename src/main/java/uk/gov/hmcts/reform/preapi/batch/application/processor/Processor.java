package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.validation.DataValidationService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.IArchiveData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.util.Optional;
import javax.annotation.Nullable;

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
    private final MigrationRecordService migrationRecordService;
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
        final MigrationRecordService migrationRecordService,
        final LoggingService loggingService
    ) {
        this.cacheService = cacheService;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.validationService = validationService;
        this.referenceDataProcessor = referenceDataProcessor;
        this.migrationService = migrationService;
        this.migrationTrackerService = migrationTrackerService;
        this.migrationRecordService = migrationRecordService;
        this.loggingService = loggingService;
    }

    // =========================
    // Main Processor Logic
    // =========================
    @Override
    public MigratedItemGroup process(Object item) throws Exception {
        try {
            if (item == null) {
                loggingService.logWarning("Processor - Received null item. Skipping.");
                return null;
            }

            loggingService.logDebug("Processor - Processing item of type: %s", item.getClass().getSimpleName());

            if (item instanceof MigrationRecord migrationRecord) {
                return processRecording(migrationRecord);
            }
 
            if (item instanceof CSVSitesData || item instanceof CSVChannelData) {
                referenceDataProcessor.process(item);
                return null;
            }

            loggingService.logError("Processor - Unsupported item type: %s", item.getClass().getName());
            return null;
        } catch (Exception e) {
            loggingService.logError("Processor - Error: %s", e.getMessage(), e);
            return null;
        }
            
    }

    private MigratedItemGroup processRecording(MigrationRecord migrationRecord) {
        String archiveId = migrationRecord.getArchiveId();
        String archiveName = migrationRecord.getArchiveName();
        VfMigrationStatus status = migrationRecord.getStatus();
        loggingService.logDebug(
            "Processing Recording: %s  with status: %s, | archiveId = %s", archiveName, status, archiveId);

        if (status == VfMigrationStatus.PENDING) {
            try {
                ExtractedMetadata extractedData = extractData(migrationRecord);
                if (extractedData == null) {
                    return null;
                }

                migrationRecordService.updateMetadataFields(archiveId, extractedData);

                // Transformation
                ProcessedRecording cleansedData = transformData(extractedData);
                if (cleansedData == null) {
                    return null;
                }

                // Check if already migrated
                if (isMigrated(cleansedData, migrationRecord)) {
                    return null;
                }

                // Validation
                if (!isValidated(cleansedData, migrationRecord)) {
                    return null;
                }
                
                loggingService.incrementProgress();           
                cacheService.dumpToFile();

                return migrationService.createMigratedItemGroup(extractedData, cleansedData);
            } catch (Exception e) {
                loggingService.logError("Error processing archive %s: %s", archiveName, e.getMessage(), e);
                migrationRecordService.updateToFailed(archiveId, "Error", e.getMessage());
                handleError(migrationRecord, "Failed to create migrated item group: " + e.getMessage(), "Error");
                return null;
            }

        }

        if (status == VfMigrationStatus.RESOLVED) {
            ExtractedMetadata extractedData = convertToExtractedMetadata(migrationRecord);
            try {
                if (extractedData == null) {
                    return null;
                }

                ProcessedRecording cleansedData = transformData(extractedData);
                if (cleansedData == null) {
                    return null;
                }

                if (!isResolvedValidated(cleansedData, migrationRecord)) {
                    return null;
                }

                loggingService.incrementProgress();           
                cacheService.dumpToFile();
                
                return migrationService.createMigratedItemGroup(extractedData, cleansedData);

            } catch (Exception e) {
                loggingService.logError(
                    "Processor - Unsupported item type: %s: %s",
                    extractedData.getArchiveName(),
                    e.getMessage(),
                    e
                );
                handleError(extractedData, "Failed to create migrated item group: " + e.getMessage(), "Error");
                return null;
            }
        }

        loggingService.logWarning("MigrationRecord with archiveId=%s has unexpected status: %s",
            migrationRecord.getArchiveId(), status);
        return null;
    }
    

    // =========================
    // Extraction, Transformation and Validation
    // =========================
    private ExtractedMetadata extractData(MigrationRecord migrationRecord) {
        ServiceResult<?> extractionResult = extractionService.process(migrationRecord);
        
        // Handle test items
        if (extractionResult.isTest()) {
            TestItem testItem = extractionResult.getTestItem();

            String reason = testItem.getReason();
            String keywords = testItem.getKeywordFound();
            String errorMsg = "Test: " + reason
                    + (keywords != null && !keywords.isEmpty()
                    ? " — keywords: " + keywords
                    : "");

            migrationRecordService.updateToFailed(migrationRecord.getArchiveId(), "Test", errorMsg);
            handleTest(testItem);
            return null;
        }

        // Handle extraction errors
        if (!extractionResult.isSuccess()) {
            migrationRecordService.updateToFailed(
                migrationRecord.getArchiveId(), extractionResult.getCategory(), extractionResult.getErrorMessage());
            handleError(migrationRecord, extractionResult.getErrorMessage(), extractionResult.getCategory());
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

    private boolean isValidated(ProcessedRecording cleansedData, MigrationRecord archiveItem) {
        ServiceResult<ProcessedRecording> result = validationService.validateProcessedRecording(
            cleansedData
        );
        if (checkForError(result, archiveItem)) {
            return false;
        }

        loggingService.logDebug("All validation rules passed");
        return true;
    }

    private boolean isResolvedValidated(ProcessedRecording cleansedData, MigrationRecord migrationRecord) {
        ServiceResult<ProcessedRecording> result = validationService.validateResolvedRecording(
            cleansedData,
            migrationRecord.getArchiveName()
        );

        if (checkForError(result, migrationRecord)) {
            return false;
        }
    
        loggingService.logDebug("All validation rules passed");
        return true;
    }

  
    private boolean isMigrated(ProcessedRecording cleansedData, MigrationRecord archiveItem) {
        Optional<MigrationRecord> maybeExisting = migrationRecordService.findByArchiveId(archiveItem.getArchiveId());

        if (maybeExisting.isPresent() && maybeExisting.get().getStatus() == VfMigrationStatus.SUCCESS) {
            loggingService.logDebug("Recording already migrated: %s", archiveItem.getArchiveId());
            handleError(archiveItem, "Duplicate archiveId already migrated", "Duplicate");
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
            migrationRecordService.updateToFailed(item.getArchiveId(), category, errorMessage);
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

    @Nullable
    private ExtractedMetadata convertToExtractedMetadata(MigrationRecord migrationRecord) {
        if (migrationRecord == null) {
            loggingService.logWarning("Migration Record is null. Skipping extraction.");
            return null;
        }

        loggingService.logInfo("Converting MigrationRecord to ExtractedMetadata: " + migrationRecord);

        String fileExtension = null;
        String fileName = migrationRecord.getFileName();
        if (fileName != null && fileName.contains(".")) {
            fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);
        }

        return new ExtractedMetadata(
            migrationRecord.getCourtReference(),
            migrationRecord.getCourtId(),
            migrationRecord.getUrn(),
            migrationRecord.getExhibitReference(),
            migrationRecord.getDefendantName(),
            migrationRecord.getWitnessName(),
            migrationRecord.getRecordingVersion(),
            migrationRecord.getRecordingVersionNumber(),
            fileExtension,
            migrationRecord.getCreateTime() != null
                ? migrationRecord.getCreateTime().toLocalDateTime()
                : null,
            migrationRecord.getDuration() != null ? migrationRecord.getDuration() : 0,
            fileName,
            migrationRecord.getFileSizeMb(),
            migrationRecord.getArchiveId(),
            migrationRecord.getArchiveName()
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

