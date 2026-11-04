package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AzureFileUploader implements IFileUploader {
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;

    @Autowired
    public AzureFileUploader(final AzureIngestStorageService azureIngestStorageService,
                             final AzureFinalStorageService azureFinalStorageService) {
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public void downloadInputFile(final EditRequest request,
                                   final String inputFileName) {
        final long downloadStart = System.currentTimeMillis();
        if (!azureFinalStorageService
            .downloadBlob(request.getSourceRecording().getId().toString(), inputFileName, inputFileName)) {
            throw new UnknownServerException("Error occurred when attempting to download file: " + inputFileName);
        }
        final long downloadEnd = System.currentTimeMillis();
        log.info("Download completed in {} ms", downloadEnd - downloadStart);
    }

    @Override
    public void uploadOutputFile(final UUID newRecordingId,
                                  final String outputFileName,
                                  final List<String> filesToDelete) {
        final long uploadStart = System.currentTimeMillis();
        if (!azureIngestStorageService.uploadBlob(outputFileName, newRecordingId + "-input", outputFileName)) {
            cleanupLocalFiles(filesToDelete);
            throw new UnknownServerException("Error occurred when attempting to upload file");
        }
        final long uploadEnd = System.currentTimeMillis();
        log.info("Upload completed in {} ms", uploadEnd - uploadStart);

        cleanupLocalFiles(filesToDelete);
    }

    @Override
    public boolean downloadBlob(String containerName, String fileName) {
        if (!azureFinalStorageService.downloadBlob(containerName, fileName, fileName)) {
            log.error("Failed to download mp4 file from container {}", containerName);
            return false;
        }
        return true;
    }

    @Override
    public void cleanupLocalFiles(final List<String> files) {
        files.forEach(this::deleteFile);
    }

    private void deleteFile(String fileName) {
        File file = new File(fileName);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            log.error("Error deleting file: {}", fileName, e);
        }
    }

}
