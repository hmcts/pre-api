package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RecordingMetadataProcessor.class)
class RecordingMetadataProcessorTest {

    @MockitoBean
    private DataExtractionService extractionService;

    @MockitoBean
    private DataTransformationService transformationService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private RecordingMetadataProcessor processor;

    @Test
    void shouldSkipIfExistingStatusIsNotPending() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("A123");

        MigrationRecord existing = new MigrationRecord();
        existing.setStatus(VfMigrationStatus.SUCCESS);

        when(migrationRecordService.findByArchiveId("A123")).thenReturn(Optional.of(existing));

        processor.processRecording(record);

        verifyNoInteractions(extractionService);
        verify(migrationRecordService, never()).updateMetadataFields(any(), any());
    }

    @Test
    void shouldSkipIfExtractionFails() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("B456");

        when(migrationRecordService.findByArchiveId("B456"))
            .thenReturn(Optional.of(record));
        when(extractionService.process(record))
            .thenReturn(ServiceResult.createErrorResult("Error", "Mock failure"));

        processor.processRecording(record);

        verify(migrationRecordService, never()).updateMetadataFields(any(), any());
    }

    @Test
    void shouldSkipIfExtractionIsTest() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("C789");

        when(migrationRecordService.findByArchiveId("C789"))
            .thenReturn(Optional.of(record));

        TestItem testItem = new TestItem(
            record,
            "Matched TEST regex pattern",
            false,
            120,
            false,
            "[]",
            true
        );

        ServiceResult<TestItem> testResult = ServiceResult.createTestResult(testItem);

        when(extractionService.process(record))
            .thenAnswer(invocation -> testResult);

        processor.processRecording(record);

        verify(migrationRecordService, never()).updateMetadataFields(any(), any());
    }

    @Test
    void shouldSkipIfTransformationFails() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("D123");

        ExtractedMetadata extracted = new ExtractedMetadata();
        extracted.setUrn("abc");
        extracted.setExhibitReference("xyz");
        extracted.setDefendantLastName("Smith");
        extracted.setWitnessFirstName("John");
        extracted.setRecordingVersion("ORIG");

        when(migrationRecordService.findByArchiveId("D123"))
            .thenReturn(Optional.of(record));

        when(extractionService.process(record))
            .thenAnswer(invocation -> ServiceResult.createSuccessResult(extracted));

        when(transformationService.transformData(extracted))
            .thenAnswer(invocation -> ServiceResult.createErrorResult("Error", "Missing"));

        processor.processRecording(record);

        verify(migrationRecordService).updateMetadataFields("D123", extracted);
        verify(migrationRecordService, never()).updateParentTempIdIfCopy(any(), any(), any());
    }

    @Test
    void shouldProcessAndUpdateParentIfCopy() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("E999");

        ExtractedMetadata extracted = new ExtractedMetadata();
        extracted.setUrn("URN");
        extracted.setExhibitReference("EXHIBIT");
        extracted.setDefendantLastName("Doe");
        extracted.setWitnessFirstName("Jane");
        extracted.setRecordingVersion("COPY");

        ProcessedRecording processed = new ProcessedRecording();
        processed.setOrigVersionNumberStr("2");

        when(migrationRecordService.findByArchiveId("E999"))
            .thenReturn(Optional.of(record));

        when(extractionService.process(record))
            .thenAnswer(invocation -> ServiceResult.createSuccessResult(extracted));

        when(transformationService.transformData(extracted))
            .thenAnswer(invocation -> ServiceResult.createSuccessResult(processed));

        processor.processRecording(record);

        verify(migrationRecordService).updateMetadataFields("E999", extracted);
        verify(migrationRecordService).updateParentTempIdIfCopy(
            eq("E999"),
            eq("urn|exhibit|jane|doe"),
            eq("2")
        );
    }

    @Test
    void shouldHandleExceptionGracefully() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveId("X999");

        when(migrationRecordService.findByArchiveId("X999")).thenThrow(new RuntimeException("Simulated"));

        processor.processRecording(record);

        verify(migrationRecordService).findByArchiveId("X999");
    }
}
