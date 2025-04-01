package uk.gov.hmcts.reform.preapi.batch.application.services;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureVodafoneMigrationService.class)
class AzureVodafoneMigrationServiceTest {

    @MockitoBean
    private AzureConfiguration configuration;

    @Mock
    private SyncPoller<BlobCopyInfo, Void> poller;

    @Test
    void copyBlob() {

        var vodafoneStorageClient = mock(BlobServiceClient.class);
        var ingestStorageClient = mock(BlobServiceClient.class);
        when(configuration.vodafoneStorageClient()).thenReturn(vodafoneStorageClient);
        when(configuration.ingestStorageClient()).thenReturn(ingestStorageClient);

        var sourceContainerClient = mock(BlobContainerClient.class);
        var destContainerClient = mock(BlobContainerClient.class);
        var sourceBlobClient = mock(BlobClient.class);
        var destBlobClient = mock(BlobClient.class);

        when(vodafoneStorageClient.getBlobContainerClient("source-container"))
            .thenReturn(sourceContainerClient);
        when(ingestStorageClient.getBlobContainerClient("dest-container"))
            .thenReturn(destContainerClient);

        when(sourceContainerClient.getBlobClient("testfile.xml"))
            .thenReturn(sourceBlobClient);
        when(destContainerClient.getBlobClient("testfile.xml"))
            .thenReturn(destBlobClient);

        when(destContainerClient.exists()).thenReturn(false);

        when(sourceBlobClient.getBlobUrl()).thenReturn("source-url");

        when(destBlobClient.beginCopy("source-url", null)).thenReturn(poller);

        when(poller.waitForCompletion()).thenReturn(null);

        var azureVodafoneMigrationService = new AzureVodafoneMigrationService(configuration);

        azureVodafoneMigrationService.copyBlob("source-container", "dest-container", "testfile.xml");

        verify(destContainerClient).create();
        verify(poller).waitForCompletion();
    }

}
