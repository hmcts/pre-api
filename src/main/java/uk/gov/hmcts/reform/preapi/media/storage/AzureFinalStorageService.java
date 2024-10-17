package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AzureFinalStorageService extends AzureStorageService {
    @Autowired
    public AzureFinalStorageService(BlobServiceClient finalStorageClient) {
        super(finalStorageClient);
    }
}
