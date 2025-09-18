package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationGroupBuilderService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.validation.DataValidationService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Processor.class)
class ProcessorTest {

    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private DataExtractionService extractionService;

    @MockitoBean
    private DataTransformationService transformationService;

    @MockitoBean
    private DataValidationService validationService;

    @MockitoBean
    private MigrationTrackerService migrationTrackerService;

    @MockitoBean
    private CaseRepository caseRepository;

    @MockitoBean
    private ReferenceDataProcessor referenceDataProcessor;

    @MockitoBean
    private MigrationGroupBuilderService migrationService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private Processor processor;

    private MigrationRecord testMigrationRecord;
    private ExtractedMetadata testExtractedMetadata;
    private ProcessedRecording testProcessedRecording;
    private MigratedItemGroup testMigratedItemGroup;

    @BeforeEach
    void setUp() {
        testMigrationRecord = createTestMigrationRecord();
        testExtractedMetadata = createTestExtractedMetadata();
        testProcessedRecording = createTestProcessedRecording();
        testMigratedItemGroup = createTestMigratedItemGroup();
    }

    // =========================
    // Main Process Method Tests
    // =========================

    @Test
    void shouldReturnNullWhenItemIsNull() throws Exception {
        MigratedItemGroup result = processor.process(null);

        assertNull(result);
        verify(loggingService).logWarning("Processor - Received null item. Skipping.");
    }

    @Test
    void shouldProcessMigrationRecordSuccessfully() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNotNull(result);
        verify(cacheService).dumpToFile();
    }

    @Test
    void shouldProcessCSVSitesDataAndReturnNull() throws Exception {
        CSVSitesData csvSitesData = new CSVSitesData();
        when(referenceDataProcessor.process(csvSitesData)).thenReturn(null);

        MigratedItemGroup result = processor.process(csvSitesData);

        assertNull(result);
        verify(referenceDataProcessor).process(csvSitesData);
    }

    @Test
    void shouldReturnNullForUnsupportedItemType() throws Exception {
        String unsupportedItem = "unsupported";

        MigratedItemGroup result = processor.process(unsupportedItem);

        assertNull(result);
        verify(loggingService).logError("Processor - Unsupported item type: %s", "java.lang.String");
    }


    @Test
    void shouldHandleExtractionFailure() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        when(extractionService.process(any(MigrationRecord.class)))
            .thenReturn(ServiceResult.error("Extraction failed", "ExtractionError"));

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(migrationRecordService).updateToFailed(
            anyString(), eq("ExtractionError"), eq("Extraction failed"));
        verify(migrationTrackerService).addFailedItem(any());
    }

    @Test
    void shouldHandleValidationFailure() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();
        when(validationService.validateProcessedRecording(any()))
            .thenReturn(ServiceResult.error("Validation failed", "ValidationError"));

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(migrationRecordService).updateToFailed(
            anyString(), eq("ValidationError"), eq("Validation failed"));
    }

    @Test
    void shouldProcessCSVChannelDataAndReturnNull() throws Exception {
        CSVChannelData csvChannelData = new CSVChannelData();
        when(referenceDataProcessor.process(csvChannelData)).thenReturn(null);

        MigratedItemGroup result = processor.process(csvChannelData);

        assertNull(result);
        verify(referenceDataProcessor).process(csvChannelData);
    }

    @Test
    void shouldReturnNullIfAlreadyMigrated() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();

        MigrationRecord existing = createTestMigrationRecord();
        existing.setStatus(VfMigrationStatus.SUCCESS);
        when(migrationRecordService.findByArchiveId(anyString())).thenReturn(Optional.of(existing));

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(migrationRecordService).findByArchiveId(testMigrationRecord.getArchiveId());
        verify(migrationTrackerService).addFailedItem(any());
    }
    // =========================
    // Pending Status Processing Tests
    // =========================

    @Test
    void shouldProcessPendingRecordingSuccessfully() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNotNull(result);
        verify(extractionService).process(testMigrationRecord);
        verify(transformationService).transformData(testExtractedMetadata);
        verify(validationService).validateProcessedRecording(testProcessedRecording);
        verify(migrationService).createMigratedItemGroup(testExtractedMetadata, testProcessedRecording);
    }

    @Test
    void shouldHandlePreExistingFailureFromExtraction() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        doReturn(ServiceResult.error("Keyword 'PRE' found", "Pre_Existing"))
            .when(extractionService).process(any(MigrationRecord.class));

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(extractionService).process(any(MigrationRecord.class));
        verify(migrationRecordService).updateToFailed(
            testMigrationRecord.getArchiveId(),
            "Pre_Existing",
            "Keyword 'PRE' found"
        );
        verify(migrationTrackerService).addFailedItem(any());
    }

    @Test
    void shouldHandleRawFileFailureFromExtraction() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        doReturn(ServiceResult.error(Constants.ErrorMessages.RAW_FILE, "Raw_Files"))
            .when(extractionService).process(any(MigrationRecord.class));

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(extractionService).process(any(MigrationRecord.class));
        verify(migrationRecordService).updateToFailed(
            testMigrationRecord.getArchiveId(),
            "Raw_Files",
            Constants.ErrorMessages.RAW_FILE
        );
        verify(migrationTrackerService).addFailedItem(any());
    }

    @Test
    void shouldSkipWhenCaseIsClosed_inPendingFlow() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();

        CreateCaseDTO closedCase = mock(CreateCaseDTO.class);
        when(closedCase.getState()).thenReturn(CaseState.CLOSED);
        doReturn(Optional.of(closedCase)).when(cacheService).getCase("1234567890");

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(migrationRecordService).updateToFailed(
            anyString(),
            eq(VfFailureReason.CASE_CLOSED.toString()),
            argThat(msg -> msg.contains("closed") && msg.contains("1234567890"))
        );
        verify(migrationTrackerService).addFailedItem(any());
        verify(migrationService, never()).createMigratedItemGroup(any(), any());
    }

    @Test
    void shouldSkipWhenCaseIsClosed_inSubmittedFlow() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.SUBMITTED);

        when(transformationService.transformData(any(ExtractedMetadata.class)))
            .thenReturn(ServiceResult.success(testProcessedRecording));
        when(validationService.validateResolvedRecording(any(ProcessedRecording.class), anyString()))
            .thenReturn(ServiceResult.success(testProcessedRecording));

        CreateCaseDTO closedCase = mock(CreateCaseDTO.class);
        when(closedCase.getState()).thenReturn(CaseState.CLOSED);
        doReturn(Optional.of(closedCase)).when(cacheService).getCase("1234567890");

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(migrationRecordService).updateToFailed(
            anyString(),
            eq(VfFailureReason.CASE_CLOSED.toString()),
            argThat(msg -> msg.contains("closed") && msg.contains("1234567890"))
        );
        verify(migrationTrackerService).addFailedItem(any());
        verify(migrationService, never()).createMigratedItemGroup(any(), any());
    }

    @Test
    void shouldProceedWhenCaseIsOpen_inPendingFlow() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();

        CreateCaseDTO openCase = mock(CreateCaseDTO.class);
        when(openCase.getState()).thenReturn(CaseState.OPEN);
        doReturn(Optional.of(openCase)).when(cacheService).getCase("1234567890");

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNotNull(result);
        verify(migrationService).createMigratedItemGroup(any(ExtractedMetadata.class), any(ProcessedRecording.class));
    }

    @Test
    void shouldProceedWhenCaseAbsent_inPendingFlow() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        setupSuccessfulProcessingMocks();

        doReturn(Optional.empty()).when(cacheService).getCase("1234567890");

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNotNull(result);
        verify(migrationService).createMigratedItemGroup(any(ExtractedMetadata.class), any(ProcessedRecording.class));
    }

    // =========================
    // Resolved Status Processing Tests
    // =========================

    @Test
    void shouldProcessResolvedRecordingSuccessfully() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.SUBMITTED);
        ServiceResult<ProcessedRecording> transformationResult = ServiceResult.success(testProcessedRecording);
        ServiceResult<ProcessedRecording> validationResult = ServiceResult.success(testProcessedRecording);
        
        when(transformationService.transformData(any(ExtractedMetadata.class))).thenReturn(transformationResult);
        when(validationService.validateResolvedRecording(
            testProcessedRecording, testMigrationRecord.getArchiveName())).thenReturn(validationResult);
        when(migrationService.createMigratedItemGroup(any(ExtractedMetadata.class), 
            eq(testProcessedRecording))).thenReturn(testMigratedItemGroup);

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNotNull(result);
        verify(validationService).validateResolvedRecording(
            testProcessedRecording, testMigrationRecord.getArchiveName());
        verify(cacheService).dumpToFile();
    }

    @Test
    void shouldHandleResolvedValidationFailure() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.SUBMITTED);
        ServiceResult<ProcessedRecording> transformationResult = ServiceResult.success(testProcessedRecording);
        ServiceResult<ProcessedRecording> validationResult = ServiceResult.error(
            "Resolved validation failed", "ResolvedValidationError");
        
        when(transformationService.transformData(any(ExtractedMetadata.class))).thenReturn(transformationResult);
        when(validationService.validateResolvedRecording(
            testProcessedRecording, testMigrationRecord.getArchiveName())).thenReturn(validationResult);

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(migrationRecordService).updateToFailed(
            testMigrationRecord.getArchiveId(), 
            "ResolvedValidationError", 
            "Resolved validation failed"
        );
    }

    @Test
    void shouldReturnNullForUnexpectedStatus() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.FAILED);

        MigratedItemGroup result = processor.process(testMigrationRecord);

        assertNull(result);
        verify(loggingService).logWarning(
            "MigrationRecord with archiveId=%s has unexpected status: %s",
            testMigrationRecord.getArchiveId(), 
            VfMigrationStatus.FAILED
        );
    }

    // =========================
    // Notification Tests
    // =========================

    @Test
    void shouldCreateNotifyItemForDoubleBarrelledDefendantName() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        testExtractedMetadata = createTestExtractedMetadata();
        testExtractedMetadata.setDefendantLastName("Smith-Jones");

        testProcessedRecording.setDefendantLastName("Smith-Jones");
        testProcessedRecording.setWitnessFirstName("Jane");
        testProcessedRecording.setUrn("12345678901");
        testProcessedRecording.setExhibitReference("EXHIBIT123");

        setupSuccessfulProcessingMocks();

        processor.process(testMigrationRecord);

        verify(migrationTrackerService).addNotifyItem(argThat(item ->
            item.getNotification().equals("Double-barrelled name")
        ));
    }

    @Test
    void shouldCreateNotifyItemForDoubleBarrelledWitnessName() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        testExtractedMetadata = createTestExtractedMetadata();
        testExtractedMetadata.setWitnessFirstName("Mary-Jane");

        testProcessedRecording.setDefendantLastName("Smith");
        testProcessedRecording.setWitnessFirstName("Mary-Jane");
        testProcessedRecording.setUrn("12345678901");
        testProcessedRecording.setExhibitReference("EXHIBIT123");
        
        setupSuccessfulProcessingMocks();

        processor.process(testMigrationRecord);

        verify(migrationTrackerService).addNotifyItem(argThat(item ->
            item.getNotification().equals("Double-barrelled name")
        ));
    }

    @Test
    void shouldCreateNotifyItemForMissingUrn() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        testExtractedMetadata = createTestExtractedMetadata();
        testExtractedMetadata.setUrn(null);
        
        testProcessedRecording.setDefendantLastName("Smith");
        testProcessedRecording.setWitnessFirstName("Mary");
        testProcessedRecording.setUrn("");
        testProcessedRecording.setExhibitReference("EXHIBIT123");
        testProcessedRecording.setCaseReference("EXHIBIT123");

        setupSuccessfulProcessingMocks();

        processor.process(testMigrationRecord);

        verify(migrationTrackerService).addNotifyItem(argThat(item ->
            item.getNotification().equals("Used Xhibit reference as URN did not meet requirements")
        ));
    }

    @Test
    void shouldCreateNotifyItemForCaseReferenceLength() throws Exception {
        testMigrationRecord.setStatus(VfMigrationStatus.PENDING);
        testProcessedRecording = createTestProcessedRecording();
        testProcessedRecording.setCaseReference("12345678");
        testProcessedRecording.setPreferred(true);
        setupSuccessfulProcessingMocks();

        processor.process(testMigrationRecord);

        verify(migrationTrackerService,  atLeastOnce()).addNotifyItem(any(NotifyItem.class));
    }

    // =========================
    // Helper Methods
    // =========================

    @SuppressWarnings("unchecked")
    private void setupSuccessfulProcessingMocks() {
        ServiceResult<ProcessedRecording> transformationResult = ServiceResult.success(testProcessedRecording);
        ServiceResult<ProcessedRecording> validationResult = ServiceResult.success(testProcessedRecording);
        
        doReturn(ServiceResult.success(testExtractedMetadata)).when(extractionService)
            .process(any(MigrationRecord.class));
        when(transformationService.transformData(any(ExtractedMetadata.class))).thenReturn(transformationResult);
        when(migrationRecordService.findByArchiveId(anyString())).thenReturn(Optional.empty());
        when(validationService.validateProcessedRecording(any(ProcessedRecording.class)))
            .thenReturn(validationResult);
        when(validationService.validateResolvedRecording(any(ProcessedRecording.class), anyString()))
            .thenReturn(validationResult);
        when(migrationService.createMigratedItemGroup(any(ExtractedMetadata.class), any(ProcessedRecording.class)))
            .thenReturn(testMigratedItemGroup);
        
        doNothing().when(migrationRecordService).updateMetadataFields(anyString(), any(ExtractedMetadata.class));
        doNothing().when(cacheService).dumpToFile();
    }

    private MigrationRecord createTestMigrationRecord() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId(UUID.randomUUID().toString());
        record.setArchiveName("test-archive.mp4");
        record.setFileName("test-recording.mp4");
        record.setCourtReference("COURT123");
        record.setUrn("12345678901");
        record.setExhibitReference("EXHIBIT123");
        record.setDefendantName("John Doe");
        record.setWitnessName("Jane Smith");
        record.setRecordingVersion("v1.0");
        record.setRecordingVersionNumber("1");
        record.setCreateTime(Timestamp.valueOf(LocalDateTime.now()));
        record.setDuration(100);
        record.setFileSizeMb("100.5");
        record.setStatus(VfMigrationStatus.PENDING);
        return record;
    }

    private ExtractedMetadata createTestExtractedMetadata() {
        return new ExtractedMetadata(
            "COURT123",
            UUID.randomUUID(),
            null,
            "12345678901",
            "EXHIBIT123",
            "John Doe",
            "Jane Smith",
            "v1.0",
            "1",
            "mp4",
            LocalDateTime.now(),
            100,
            "test-recording.mp4",
            "100",
            UUID.randomUUID().toString(),
            "test-archive.mp4"
        );
    }

    private ProcessedRecording createTestProcessedRecording() {
        ProcessedRecording recording = new ProcessedRecording();
        recording.setPreferred(true);
        recording.setCaseReference("1234567890");
        recording.setExhibitReference("EXHIBIT1234");
        return recording;
    }

    private MigratedItemGroup createTestMigratedItemGroup() {
        MigratedItemGroup group = new MigratedItemGroup();
        return group;
    }

    
}