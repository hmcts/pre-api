package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.extraction.DataExtractionService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.transformation.DataTransformationService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.batch.util.RecordingUtils;
import uk.gov.hmcts.reform.preapi.batch.util.ServiceResultUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { RecordingMetadataProcessor.class })
public class RecordingMetadataProcessorTest {
    @MockitoBean
    private DataExtractionService dataExtractionService;

    @MockitoBean
    private DataTransformationService transformationService;

    @MockitoBean
    private InMemoryCacheService cacheService;

    @Autowired
    private RecordingMetadataProcessor recordingMetadataProcessor;

    private MockedStatic<ServiceResultUtil> serviceResultUtil;
    private MockedStatic<RecordingUtils> recordingUtils;

    @BeforeEach
    void setUp() {
        serviceResultUtil = mockStatic(ServiceResultUtil.class);
        recordingUtils = mockStatic(RecordingUtils.class);
    }

    @AfterEach
    void tearDown() {
        serviceResultUtil.close();
        recordingUtils.close();
    }

    @Test
    @DisplayName("Process recording on exception create failure result")
    void processRecordingOnError() {
        doThrow(RuntimeException.class).when(dataExtractionService).process(any());
        recordingMetadataProcessor.processRecording(null);

        serviceResultUtil.verify(() -> ServiceResultUtil.failure(any(), eq("Error")));
        serviceResultUtil.verify(() -> ServiceResultUtil.failure(any(), eq("Error")));
    }

    @Test
    @DisplayName("Process recording when extraction service results in error")
    void processRecordingExtractionError() {
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData))
            .thenReturn(new ServiceResult<>("error message", "error"));

        recordingMetadataProcessor.processRecording(null);

        verify(transformationService, never()).transformData(any());
    }

    @Test
    @DisplayName("Process recording when extraction service finds recording is test")
    void processRecordingExtractionIsTest() {
        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        when(dataExtractionService.process(csvArchiveListData))
            .thenReturn(new ServiceResult<>(new TestItem(), true));

        recordingMetadataProcessor.processRecording(null);

        verify(transformationService, never()).transformData(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Process recording when transformation finds null data")
    void processRecordingTransformationFindsNull() {
        var metadata = mock(ServiceResult.class);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        when(metadata.getData()).thenReturn(extractedMetadata);
        when(dataExtractionService.process(any()))
            .thenReturn(metadata);

        ServiceResult<ProcessedRecording> metadata2 = mock(ServiceResult.class);
        when(metadata2.getData()).thenReturn(null);
        when(transformationService.transformData(any())).thenReturn(metadata2);

        recordingMetadataProcessor.processRecording(null);

        verify(transformationService, times(1)).transformData(any());
        verify(cacheService, never()).generateCacheKey(any(), any(), any(), any(), any());
        serviceResultUtil.verify(() -> ServiceResultUtil.failure(
            "Data not transformed successfully", "Missing data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Process recording when transformation finds null existing metadata")
    void processRecordingTransformationFindsDataExistingMetadataNull() {
        var metadata = mock(ServiceResult.class);
        ExtractedMetadata extractedMetadata = new ExtractedMetadata();
        when(metadata.getData()).thenReturn(extractedMetadata);
        when(dataExtractionService.process(any()))
            .thenReturn(metadata);

        ServiceResult<ProcessedRecording> metadata2 = mock(ServiceResult.class);
        ProcessedRecording processedRecording = ProcessedRecording.builder()
            .urn("urn")
            .exhibitReference("exhibitReference")
            .defendantLastName("defendantLastName")
            .witnessFirstName("witnessFirstName")
            .recordingVersion("1")
            .recordingVersionNumberStr("1")
            .build();
        when(metadata2.getData()).thenReturn(processedRecording);
        when(transformationService.transformData(any())).thenReturn(metadata2);
        when(cacheService.generateCacheKey(
            eq("recording"),
            eq("version"),
            eq("urn"),
            eq("exhibitReference"),
            eq("defendantLastName"),
            eq("witnessFirstName"))).thenReturn("key");
        when(cacheService.getHashAll("key")).thenReturn(null);

        CSVArchiveListData csvArchiveListData = new CSVArchiveListData();
        csvArchiveListData.setArchiveName("archiveName");
        recordingMetadataProcessor.processRecording(csvArchiveListData);

        verify(transformationService, times(1)).transformData(any());
        verify(cacheService, times(1)).generateCacheKey(any(), any(), any(), any(), any(), any());
        verify(cacheService, times(1)).getHashAll("key");
        recordingUtils.verify(() -> RecordingUtils.updateVersionMetadata(
            eq("1"),
            eq("1"),
            eq("archiveName"),
            any()
        ));
    }
}
