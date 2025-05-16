package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { Processor.class })
public class ProcessorTest {
    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private DataExtractionService dataExtractionService;

    @MockitoBean
    private DataTransformationService dataTransformationService;

    @MockitoBean
    private DataValidationService dataValidationService;

    @MockitoBean
    private MigrationTrackerService migrationTrackerService;

    @MockitoBean
    private ReferenceDataProcessor referenceDataProcessor;

    @MockitoBean
    private MigrationGroupBuilderService  migrationGroupBuilderService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private Processor processor;

    @Test
    @DisplayName("Should log warning and return null when processed item is null")
    void processNull() throws Exception {
        assertThat(processor.process(null)).isNull();

        verify(loggingService, times(1))
            .logWarning("Received null item to process");
    }

    @Test
    @DisplayName("Should process sites data")
    void processSitesData() throws Exception {
        assertThat(processor.process(new CSVSitesData())).isNull();

        verify(referenceDataProcessor, times(1))
            .process(any(CSVSitesData.class));
    }

    @Test
    @DisplayName("Should process channel data")
    void processChannelData() throws Exception {
        assertThat(processor.process(new CSVChannelData())).isNull();

        verify(referenceDataProcessor, times(1))
            .process(any(CSVChannelData.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is a test")
    void processArchiveItemIsTest() throws Exception {
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        ServiceResult result = mock(ServiceResult.class);
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);
        when(result.isTest()).thenReturn(true);
        when(result.getTestItem()).thenReturn(new TestItem());

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);
        verify(migrationTrackerService, times(1)).addTestItem(any(TestItem.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is not a success")
    void processArchiveItemIsNotSuccess() throws Exception {
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        ServiceResult result = mock(ServiceResult.class);
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);
        when(result.isSuccess()).thenReturn(false);
        when(result.getErrorMessage()).thenReturn("Error message");
        when(result.getCategory()).thenReturn("Error");

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);
        verify(migrationTrackerService, times(1)).addFailedItem(any(FailedItem.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is a success with double barreled defendant name")
    void processArchiveItemIsSuccess() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendant-LastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:1234567890");
        extractedMetadata.setExhibitReference("exhibitReference");

        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);

        var captor = ArgumentCaptor.forClass(NotifyItem.class);
        verify(migrationTrackerService, times(1)).addNotifyItem(captor.capture());
        assertThat(captor.getValue().getNotification()).isEqualTo("Double-barreled defendant");
        assertThat(captor.getValue().getExtractedMetadata()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is a success with missing urn")
    void processArchiveItemIsSuccessMissingUrn() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("");
        extractedMetadata.setExhibitReference("exhibitReference");

        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);

        var captor = ArgumentCaptor.forClass(NotifyItem.class);
        verify(migrationTrackerService, times(1)).addNotifyItem(captor.capture());
        assertThat(captor.getValue().getNotification()).isEqualTo("Missing URN");
        assertThat(captor.getValue().getExtractedMetadata()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is a success with short urn")
    void processArchiveItemIsSuccessShortUrn() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn");
        extractedMetadata.setExhibitReference("exhibitReference");

        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);

        var captor = ArgumentCaptor.forClass(NotifyItem.class);
        verify(migrationTrackerService, times(1)).addNotifyItem(captor.capture());
        assertThat(captor.getValue().getNotification()).isEqualTo("URN - invalid length");
        assertThat(captor.getValue().getExtractedMetadata()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is a success with missing exhibit reference")
    void processArchiveItemIsSuccessMissingExhibitRef() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:0123456789");
        extractedMetadata.setExhibitReference("");

        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);

        var captor = ArgumentCaptor.forClass(NotifyItem.class);
        verify(migrationTrackerService, times(1)).addNotifyItem(captor.capture());
        assertThat(captor.getValue().getNotification()).isEqualTo("Missing Exhibit Ref");
        assertThat(captor.getValue().getExtractedMetadata()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is a success with short exhibit reference")
    void processArchiveItemIsSuccessShortExhibitRef() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:0123456789");
        extractedMetadata.setExhibitReference("ref");

        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);

        var captor = ArgumentCaptor.forClass(NotifyItem.class);
        verify(migrationTrackerService, times(1)).addNotifyItem(captor.capture());
        assertThat(captor.getValue().getNotification()).isEqualTo("T-ref - invalid length");
        assertThat(captor.getValue().getExtractedMetadata()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that fails transformation")
    void processArchiveItemTransformationError() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:0123456789");
        extractedMetadata.setExhibitReference("exhibitReference");
        extractedMetadata.setSanitizedArchiveName("sanitizedArchiveName");
        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        ServiceResult transformationResult = mock(ServiceResult.class);
        when(transformationResult.getErrorMessage()).thenReturn("Error message");
        when(transformationResult.getCategory()).thenReturn("Error");
        when(dataTransformationService.transformData(any())).thenReturn(transformationResult);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);
        verify(dataTransformationService, times(1)).transformData(any());
        verify(migrationTrackerService, times(1)).addFailedItem(any());
        verify(loggingService, times(1))
            .logError("Failed to transform archive: %s", extractedMetadata.getSanitizedArchiveName());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that is migrated")
    void processArchiveItemIsMigrated() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:0123456789");
        extractedMetadata.setExhibitReference("exhibitReference");
        extractedMetadata.setSanitizedArchiveName("sanitizedArchiveName");
        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        ServiceResult transformationResult = mock(ServiceResult.class);
        ProcessedRecording processedRecording = mock(ProcessedRecording.class);
        when(processedRecording.getCaseReference()).thenReturn("caseReference");
        when(transformationResult.getData()).thenReturn(processedRecording);
        when(dataTransformationService.transformData(any())).thenReturn(transformationResult);

        // already migrated
        when(cacheService.getCase(processedRecording.getCaseReference())).thenReturn(Optional.of(mock(CaseDTO.class)));

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);
        verify(dataTransformationService, times(1)).transformData(any());
        verify(cacheService, times(1)).getCase("caseReference");
        verify(migrationTrackerService, times(1)).addFailedItem(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that fails validation")
    void processArchiveItemFailsValidation() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:0123456789");
        extractedMetadata.setExhibitReference("exhibitReference");
        extractedMetadata.setSanitizedArchiveName("sanitizedArchiveName");
        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        ServiceResult transformationResult = mock(ServiceResult.class);
        ProcessedRecording processedRecording = mock(ProcessedRecording.class);
        when(processedRecording.getCaseReference()).thenReturn("caseReference");
        when(transformationResult.getData()).thenReturn(processedRecording);
        when(dataTransformationService.transformData(any())).thenReturn(transformationResult);
        when(cacheService.getCase(processedRecording.getCaseReference())).thenReturn(Optional.empty());

        ServiceResult validationResult = mock(ServiceResult.class);
        when(validationResult.getErrorMessage()).thenReturn("Error message");
        when(validationResult.getCategory()).thenReturn("Error");
        when(dataValidationService.validateProcessedRecording(any(), any())).thenReturn(validationResult);

        assertThat(processor.process(csvArchiveListData)).isNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);
        verify(dataTransformationService, times(1)).transformData(any());
        verify(cacheService, times(1)).getCase("caseReference");
        verify(dataValidationService, times(1)).validateProcessedRecording(any(), any());
        verify(migrationTrackerService, times(1)).addFailedItem(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process archive item that fails validation")
    void processArchiveItemSuccess() throws Exception {
        ServiceResult result = mock(ServiceResult.class);
        when(result.isSuccess()).thenReturn(true);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        extractedMetadata.setDefendantLastName("defendantLastName");
        extractedMetadata.setWitnessFirstName("witnessFirstName");
        extractedMetadata.setUrn("urn:0123456789");
        extractedMetadata.setExhibitReference("exhibitReference");
        extractedMetadata.setSanitizedArchiveName("sanitizedArchiveName");
        when(result.getData()).thenReturn(extractedMetadata);
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData)).thenReturn(result);

        ServiceResult transformationResult = mock(ServiceResult.class);
        ProcessedRecording processedRecording = mock(ProcessedRecording.class);
        when(processedRecording.getCaseReference()).thenReturn("caseReference");
        when(transformationResult.getData()).thenReturn(processedRecording);
        when(dataTransformationService.transformData(any())).thenReturn(transformationResult);
        when(cacheService.getCase(processedRecording.getCaseReference())).thenReturn(Optional.empty());

        ServiceResult validationResult = mock(ServiceResult.class);
        when(dataValidationService.validateProcessedRecording(any(), any())).thenReturn(validationResult);

        when(migrationGroupBuilderService.createMigratedItemGroup(any(), any()))
            .thenReturn(mock(MigratedItemGroup.class));

        assertThat(processor.process(csvArchiveListData)).isNotNull();

        verify(dataExtractionService, times(1)).process(csvArchiveListData);
        verify(dataTransformationService, times(1)).transformData(any());
        verify(cacheService, times(1)).getCase("caseReference");
        verify(dataValidationService, times(1)).validateProcessedRecording(any(), any());
        verify(loggingService, times(1)).incrementProgress();
        verify(cacheService, times(1)).dumpToFile();
        verify(migrationGroupBuilderService, times(1)).createMigratedItemGroup(any(), any());

        verify(migrationTrackerService, never()).addFailedItem(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process exception list data successfully")
    void processExceptionItem() throws Exception {
        CSVExemptionListData data = generateCSVExemptionListData();
        ServiceResult transformationResult = mock(ServiceResult.class);
        when(dataTransformationService.transformData(any())).thenReturn(transformationResult);
        ProcessedRecording processedRecording = mock(ProcessedRecording.class);
        when(processedRecording.getCaseReference()).thenReturn("caseReference");
        when(transformationResult.getData()).thenReturn(processedRecording);
        when(migrationGroupBuilderService.createMigratedItemGroup(any(), any()))
            .thenReturn(mock(MigratedItemGroup.class));

        assertThat(processor.process(data)).isNotNull();

        verify(loggingService, times(1)).logInfo(startsWith("Converting Exemption Item to ExtractedMetadata: "));
        verify(dataTransformationService, times(1)).transformData(any());
        verify(migrationGroupBuilderService, times(1)).createMigratedItemGroup(any(), any());
        verify(loggingService, never()).logError(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should handle error on process exception list data")
    void processExceptionItemError() throws Exception {
        CSVExemptionListData data = generateCSVExemptionListData();
        ServiceResult transformationResult = mock(ServiceResult.class);
        when(dataTransformationService.transformData(any())).thenReturn(transformationResult);
        ProcessedRecording processedRecording = mock(ProcessedRecording.class);
        when(processedRecording.getCaseReference()).thenReturn("caseReference");
        when(transformationResult.getData()).thenReturn(processedRecording);

        doThrow(RuntimeException.class).when(migrationGroupBuilderService).createMigratedItemGroup(any(), any());

        assertThat(processor.process(data)).isNull();

        verify(loggingService, times(1)).logInfo(startsWith("Converting Exemption Item to ExtractedMetadata: "));
        verify(dataTransformationService, times(1)).transformData(any());
        verify(migrationGroupBuilderService, times(1)).createMigratedItemGroup(any(), any());
        verify(loggingService, times(1)).logError(any(), any(), any(), any());
        verify(migrationTrackerService, times(1)).addFailedItem(any(FailedItem.class));
    }

    private static CSVExemptionListData generateCSVExemptionListData() {
        CSVExemptionListData data = new CSVExemptionListData();
        data.setCourtReference("courtReference");
        data.setUrn("urn");
        data.setExhibitReference("exhibitReference");
        data.setDefendantName("defendantName");
        data.setWitnessName("witnessName");
        data.setRecordingVersion("1");
        data.setRecordingVersionNumber(1);
        data.setFileExtension(".mp4");
        data.setCreateTime(Timestamp.from(Instant.now()).toString());
        data.setDuration(30);
        data.setFileName("fileName.mp4");
        data.setFileSize("1024");
        data.setArchiveName("archiveName");
        return data;
    }
}
