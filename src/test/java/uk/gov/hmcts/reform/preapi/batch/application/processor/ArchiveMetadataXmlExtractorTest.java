package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.startsWith;
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
}