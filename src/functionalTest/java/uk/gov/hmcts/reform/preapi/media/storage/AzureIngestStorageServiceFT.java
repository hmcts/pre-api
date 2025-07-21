package uk.gov.hmcts.reform.preapi.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class AzureIngestStorageServiceFT extends FunctionalTestBase {
    private static final String UPLOAD_FILE = "src/functionalTest/resources/test/edit/edit_from_csv.csv";
    private static final String FILE_NAME = "edit_from_csv.csv";

    @Autowired
    private AzureIngestStorageService azureIngestStorageService;

    @Test
    @DisplayName("Should add tags to blob")
    public void markContainerAsProcessingTagging() {
        String containerName = "test-" + RandomStringUtils.secure().nextAlphabetic(10).toLowerCase();
        log.info("Creating blob container with name: {}", containerName);

        try {
            azureIngestStorageService.doesContainerExist(containerName);
            azureIngestStorageService.uploadBlob(UPLOAD_FILE, containerName, FILE_NAME);
            azureIngestStorageService.markContainerAsProcessing(containerName);

            Map<String, String> tags = azureIngestStorageService.getBlob(containerName, FILE_NAME).getTags();
            assertThat(tags).isNotNull();
            assertThat(tags.size()).isEqualTo(1);
            assertThat(tags.get("status")).isEqualTo("processing");
        } finally {
            log.info("Deleting blob container with name: {}", containerName);
            azureIngestStorageService.deleteContainer(containerName);
        }
    }
}
