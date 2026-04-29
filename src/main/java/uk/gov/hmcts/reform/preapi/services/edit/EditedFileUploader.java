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
// Handles download, upload and cleanup for Editing Service
public class EditedFileUploader {
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;

    @Autowired
    public EditedFileUploader(final AzureIngestStorageService azureIngestStorageService,
                              final AzureFinalStorageService azureFinalStorageService) {
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    protected void downloadInputFile(final EditRequest request,
                                   final String inputFileName) {
        final long downloadStart = System.currentTimeMillis();
        if (!azureFinalStorageService
            .downloadBlob(request.getSourceRecording().getId().toString(), inputFileName, inputFileName)) {
            throw new UnknownServerException("Error occurred when attempting to download file: " + inputFileName);
        }
        final long downloadEnd = System.currentTimeMillis();
        log.info("Download completed in {} ms", downloadEnd - downloadStart);
    }

    protected void uploadOutputFile(final UUID newRecordingId,
                                  final String outputFileName,
                                  final List<String> filesToDelete) {
        final long uploadStart = System.currentTimeMillis();
        if (!azureIngestStorageService.uploadBlob(outputFileName, newRecordingId + "-input", outputFileName)) {
            cleanup(filesToDelete);
            throw new UnknownServerException("Error occurred when attempting to upload file");
        }
        final long uploadEnd = System.currentTimeMillis();
        log.info("Upload completed in {} ms", uploadEnd - uploadStart);

        cleanup(filesToDelete);
    }

    protected boolean downloadBlob(String containerName, String fileName) {
        if (!azureFinalStorageService.downloadBlob(containerName, fileName, fileName)) {
            log.error("Failed to download mp4 file from container {}", containerName);
            return false;
        }
        return true;
    }

    protected void cleanup(final List<String> files) {
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
