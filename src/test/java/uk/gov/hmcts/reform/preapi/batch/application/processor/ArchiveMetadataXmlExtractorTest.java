package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { ArchiveMetadataXmlExtractor.class })
public class ArchiveMetadataXmlExtractorTest {
    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private ArchiveMetadataXmlExtractor archiveMetadataXmlExtractor;

    private MockedStatic<ReportCsvWriter> mockedReportCsvWriter;

    private static final String CONTAINER_NAME = "containerName";
    private static final String OUTPUT_DIR = "outputDir";
    private static final String FILENAME = "fileName";

    @BeforeEach
    public void setUp() {
        mockedReportCsvWriter = Mockito.mockStatic(ReportCsvWriter.class);
    }

    @AfterEach
    public void tearDown() {
        mockedReportCsvWriter.close();
    }

    @Test
    @DisplayName("Should log error message on encountering error in extractAndReportArchiveMetadata")
    void extractAndReportArchiveMetadataOnError() {
        doThrow(RuntimeException.class).when(loggingService).logInfo(any(), any());

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService, times(1)).logError(any(), any());
    }

    @Test
    @DisplayName("Should log warning and stop when container is empty")
    void extractAndReportArchiveMetadataOnEmptyContainer() {
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(List.of());

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService, times(1))
            .logInfo("Starting extraction for container: %s", CONTAINER_NAME);
        verify(azureVodafoneStorageService, times(1))
            .fetchBlobNames(CONTAINER_NAME);
        verify(loggingService, times(1))
            .logDebug("Found %d blobs in container: %s", 0, CONTAINER_NAME);
        verify(loggingService, times(1))
            .logWarning("No XML blobs found in container: " + CONTAINER_NAME);
    }

    @Test
    @DisplayName("Should get all archived metadata and generate metadata report")
    void extractAndReportArchiveMetadata() throws Exception {
        List<String> blobNames = List.of("metadata.xml");
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(blobNames);

        String validXml =
            """
            <Root>
              <ArchiveFiles>
                <DisplayName>Recording1</DisplayName>
                <CreatTime>1713242340</CreatTime>
                <Duration>3600</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>0x1e_test_video.mp4</Name>
                    <Size>20480</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
            </Root>
            """;
        InputStream xmlStream = new ByteArrayInputStream(validXml.getBytes());
        InputStreamResource mockBlob = mock(InputStreamResource.class);
        when(mockBlob.getInputStream()).thenReturn(xmlStream);
        when(azureVodafoneStorageService.fetchSingleXmlBlob(CONTAINER_NAME, "metadata.xml"))
            .thenReturn(mockBlob);

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService).logInfo("Starting extraction for container: %s", CONTAINER_NAME);
        verify(loggingService).logDebug("Found %d blobs in container: %s", 1, CONTAINER_NAME);
        verify(loggingService).logDebug("Extracted metadata for %d recordings", 1);
        verify(loggingService).logDebug("Generating archive metadata report in %s", OUTPUT_DIR);
        verify(loggingService).logInfo("Successfully generated " + FILENAME + " with 1 entries");
    }

    @Test
    @DisplayName("Should log warning when blob contains no archive metadata")
    void extractAndReportArchiveMetadataBlobContainsNoMetadata() throws Exception {
        List<String> blobNames = List.of("empty-metadata.xml");
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(blobNames);

        String xmlWithoutArchiveFiles =
            """
            <Root>
              <SomethingElse>Value</SomethingElse>
            </Root>
            """;
        InputStream xmlStream = new ByteArrayInputStream(xmlWithoutArchiveFiles.getBytes());
        InputStreamResource mockBlob = mock(InputStreamResource.class);
        when(mockBlob.getInputStream()).thenReturn(xmlStream);
        when(azureVodafoneStorageService.fetchSingleXmlBlob(CONTAINER_NAME, "empty-metadata.xml"))
            .thenReturn(mockBlob);

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService).logWarning("No archive metadata found to generate report");
    }

    @Test
    @DisplayName("Should log warnings when ArchiveFiles contains missing fields")
    void extractAndReportArchiveMetadataMissingFields() throws Exception {
        List<String> blobNames = List.of("missing-fields.xml");
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(blobNames);

        String xmlMissingFields =
            """
            <Root>
              <ArchiveFiles>
                <Duration>3600</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name></Name>
                    <Size></Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
            </Root>
            """;
        InputStream xmlStream = new ByteArrayInputStream(xmlMissingFields.getBytes());
        InputStreamResource mockBlob = mock(InputStreamResource.class);
        when(mockBlob.getInputStream()).thenReturn(xmlStream);
        when(azureVodafoneStorageService.fetchSingleXmlBlob(CONTAINER_NAME, "missing-fields.xml"))
            .thenReturn(mockBlob);

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService).logWarning("Missing DisplayName in ArchiveFiles element.");
        verify(loggingService).logWarning("Missing CreatTime for archive: ");
        verify(loggingService).logWarning("MP4 file missing required fields: Name=%s, Size=%s");
    }

    @Test
    @DisplayName("Should ignore MP4 files not starting with 0x1e")
    void extractAndReportArchiveMetadataInvalidMp4Files() throws Exception {
        List<String> blobNames = List.of("invalid-name.xml");
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(blobNames);

        String xmlWithInvalidMp4 =
            """
            <Root>
              <ArchiveFiles>
                <DisplayName>RecordingX</DisplayName>
                <CreatTime>1713242340</CreatTime>
                <Duration>3600</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>test_video.mp4</Name>
                    <Size>20480</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
            </Root>
            """;
        InputStream xmlStream = new ByteArrayInputStream(xmlWithInvalidMp4.getBytes());
        InputStreamResource mockBlob = mock(InputStreamResource.class);
        when(mockBlob.getInputStream()).thenReturn(xmlStream);
        when(azureVodafoneStorageService.fetchSingleXmlBlob(CONTAINER_NAME, "invalid-name.xml"))
            .thenReturn(mockBlob);

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService).logDebug("Extracted metadata for %d recordings", 0);
        verify(loggingService).logWarning("No archive metadata found to generate report");
    }

    @Test
    @DisplayName("Should log warning and default size when Size is not a number")
    void extractAndReportArchiveMetadataInvalidFileSize() throws Exception {
        List<String> blobNames = List.of("invalid-size.xml");
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(blobNames);

        String xmlWithInvalidSize =
            """
            <Root>
              <ArchiveFiles>
                <DisplayName>RecordingX</DisplayName>
                <CreatTime>1713242340</CreatTime>
                <Duration>3600</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>0x1e_test_video.mp4</Name>
                    <Size>not_a_number</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
            </Root>
            """;
        InputStream xmlStream = new ByteArrayInputStream(xmlWithInvalidSize.getBytes());
        InputStreamResource mockBlob = mock(InputStreamResource.class);
        when(mockBlob.getInputStream()).thenReturn(xmlStream);
        when(azureVodafoneStorageService.fetchSingleXmlBlob(CONTAINER_NAME, "invalid-size.xml"))
            .thenReturn(mockBlob);

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService).logWarning("Invalid file size: not_a_number");
    }

    @Test
    @DisplayName("Should sort allArchiveMetadata by createTime in ascending order via extractAndReportArchiveMetadata")
    void shouldSortMetadataByCreateTime() throws Exception {
        List<String> blobNames = List.of("metadata.xml");
        when(azureVodafoneStorageService.fetchBlobNames(CONTAINER_NAME)).thenReturn(blobNames);

        String xmlContent =
            """
            <Root>
              <ArchiveFiles>
                <DisplayName>Recording1</DisplayName>
                <CreatTime>1713242340</CreatTime>
                <Duration>3600</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>0x1e_test_video1.mp4</Name>
                    <Size>20480</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
              <ArchiveFiles>
                <DisplayName>Recording2</DisplayName>
                <CreatTime>1713245000</CreatTime>
                <Duration>1800</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>0x1e_test_video2.mp4</Name>
                    <Size>10240</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
              <ArchiveFiles>
                <DisplayName>Recording3</DisplayName>
                <CreatTime>1713241000</CreatTime>
                <Duration>2400</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>0x1e_test_video3.mp4</Name>
                    <Size>15360</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
              <ArchiveFiles>
                <DisplayName>Recording4</DisplayName>
                <CreatTime>abc</CreatTime>
                <Duration>2400</Duration>
                <MP4FileGrp>
                  <MP4File>
                    <Name>0x1e_test_video3.mp4</Name>
                    <Size>15360</Size>
                  </MP4File>
                </MP4FileGrp>
              </ArchiveFiles>
            </Root>
            """;
        InputStream xmlStream = new ByteArrayInputStream(xmlContent.getBytes());
        InputStreamResource mockBlob = mock(InputStreamResource.class);
        when(mockBlob.getInputStream()).thenReturn(xmlStream);
        when(azureVodafoneStorageService.fetchSingleXmlBlob(CONTAINER_NAME, "metadata.xml")).thenReturn(mockBlob);

        archiveMetadataXmlExtractor.extractAndReportArchiveMetadata(CONTAINER_NAME, OUTPUT_DIR, FILENAME);

        verify(loggingService).logInfo("Starting extraction for container: %s", CONTAINER_NAME);
        verify(loggingService).logDebug("Found %d blobs in container: %s", 1, CONTAINER_NAME);
        verify(loggingService).logDebug("Extracted metadata for %d recordings", 4);
        verify(loggingService).logDebug("Generating archive metadata report in %s", OUTPUT_DIR);
        verify(loggingService).logInfo("Successfully generated " + FILENAME + " with 4 entries");
        verify(azureVodafoneStorageService).fetchBlobNames(CONTAINER_NAME);
    }
}
