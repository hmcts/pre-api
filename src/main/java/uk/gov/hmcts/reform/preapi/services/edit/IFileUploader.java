package uk.gov.hmcts.reform.preapi.services.edit;

import uk.gov.hmcts.reform.preapi.entities.EditRequest;

import java.util.List;
import java.util.UUID;

// Handles download, upload and cleanup for Editing Service
public interface IFileUploader {

    void downloadInputFile(EditRequest request,
                           String inputFileName);

    void uploadOutputFile(UUID newRecordingId,
                          String outputFileName,
                          List<String> filesToDelete);

    boolean downloadBlob(String containerName, String fileName);

    void cleanupLocalFiles(List<String> files);
}
