package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@SpringBootTest(classes = ArchiveMetadataXmlExtractor.class)
class ArchiveMetadataXmlExtractorTest {

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private ArchiveMetadataXmlExtractor extractor;

    @Test
    void shouldLogWarningIfNoBlobsFound() {
        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(anyString(), anyString()))
            .thenReturn(List.of());

        extractor.extractAndReportArchiveMetadata("empty-container", "prefix/", "some/dir", "out");

        verify(loggingService).logWarning("No XML blobs found in container: %sempty-container");
    }

    @Test
    void shouldHandleInvalidXmlBlobGracefully() throws Exception {
        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(anyString(), anyString()))
            .thenReturn(List.of("broken.xml"));

        when(azureVodafoneStorageService.fetchSingleXmlBlob(anyString(), anyString()))
            .thenThrow(new RuntimeException("Corrupt XML"));

        extractor.extractAndReportArchiveMetadata("container", "prefix", "dir", "report");

        verify(loggingService).logError(startsWith("Failed to process blob:"), eq("broken.xml"), any(Exception.class));
    }



    @Test
    void shouldExtractMetadataAndInsertRecord() throws Exception {
        String validXml = """
            <ArchiveFiles>
                <ArchiveID>abc123</ArchiveID>
                <DisplayName>Test-230101-CASE1-WITNESS-ORIG</DisplayName>
                <CreatTime>1731681144000</CreatTime>
                <Duration>100</Duration>
                <MP4FileGrp>
                    <MP4File>
                        <Name>0x1e_test_file.mp4</Name>
                        <Size>2048</Size>
                    </MP4File>
                </MP4FileGrp>
            </ArchiveFiles>
            """;

        AzureVodafoneStorageService azureVodafoneStorageService = mock(AzureVodafoneStorageService.class);
        MigrationRecordService migrationRecordService = mock(MigrationRecordService.class);
        LoggingService loggingService = mock(LoggingService.class);

        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(anyString(), anyString()))
            .thenReturn(List.of("blob1.xml"));

        InputStreamResource xmlResource = new InputStreamResource(
            new ByteArrayInputStream(validXml.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(anyString(), anyString()))
            .thenReturn(xmlResource);

        when(migrationRecordService.insertPendingFromXml(any(), any(), any(), any(), any(), any()))
            .thenReturn(true);

        ArchiveMetadataXmlExtractor extractor = new ArchiveMetadataXmlExtractor(
            azureVodafoneStorageService,
            migrationRecordService,
            loggingService
        );

        extractor.extractAndReportArchiveMetadata("test-container", "prefix", "out", "report");

        verify(migrationRecordService).insertPendingFromXml(
            eq("abc123"),
            eq("Test-230101-CASE1-WITNESS-ORIG"),
            eq("1731681144000"),
            eq("100"),
            eq("abc123/0x1e_test_file.mp4"),
            eq("2.00")
        );

        verify(loggingService).logInfo(
            eq("Successfully generated %s.csv with %d entries"),
            eq("report"),
            eq(1)
        );
    }
}