package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AzureFinalStorageService extends AzureStorageService {

    @Autowired
    public AzureFinalStorageService(BlobServiceClient finalStorageClient) {
        super(finalStorageClient);
    }
}
