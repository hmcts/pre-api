package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4Metadata;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4XmlParser;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
public class VodafoneSyncFixTask extends RobotUserTask {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final VodafonePreferredMp4XmlParser vodafonePreferredMp4XmlParser;
    private final CommandExecutor commandExecutor;

    @Value("${tasks.vodafone-sync-fix.container:${tasks.batch-import-missing-mk-assets.mp4-source-container}}")
    private String containerName;

    @Value("${tasks.vodafone-sync-fix.script-path:bin/PRE_synch_fix.sh}")
    private String scriptPath;

    @Value("${tasks.vodafone-sync-fix.working-directory:build/reports/vf-sync-fix}")
    private String workingDirectory;

    @Value("${tasks.vodafone-sync-fix.dry-run:true}")
    private boolean dryRun;

    @Value("${tasks.vodafone-sync-fix.cleanup-downloads:true}")
    private boolean cleanupDownloads;

    @Value("${tasks.vodafone-sync-fix.file-limit:0}")
    private int fileLimit;

    @Autowired
    public VodafoneSyncFixTask(UserService userService,
                               UserAuthenticationService userAuthenticationService,
                               @Value("${vodafone-user-email}") String cronUserEmail,
                               AzureVodafoneStorageService azureVodafoneStorageService,
                               VodafonePreferredMp4XmlParser vodafonePreferredMp4XmlParser,
                               CommandExecutor commandExecutor) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.vodafonePreferredMp4XmlParser = vodafonePreferredMp4XmlParser;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void run() {
        log.info("Starting Vodafone sync-fix task for container {}", containerName);
        signInRobotUser();

        Path runDirectory = createRunDirectory();
        Path downloadDirectory = runDirectory.resolve("downloads");
        Path selectionReport = runDirectory.resolve("selected-mp4s.csv");
        Path scriptReport = runDirectory.resolve("sync-fix-dry-run.csv");
        Path summaryReport = runDirectory.resolve("summary.txt");

        try {
            Files.createDirectories(downloadDirectory);

            List<String> xmlBlobNames = azureVodafoneStorageService.fetchBlobNames(containerName);
            SelectionResult selectionResult = collectPreferredMp4Files(xmlBlobNames);

            DownloadResult downloadResult = downloadSelectedMp4Files(downloadDirectory, selectionResult.selectedMp4Files());
            writeSelectionReport(selectionReport, downloadResult);

            String scriptOutput = "";
            if (!downloadResult.downloadedFiles().isEmpty()) {
                scriptOutput = runSyncScript(downloadDirectory, scriptReport);
            } else {
                log.info("No MP4 files were downloaded successfully, skipping sync script execution");
            }

            writeSummaryReport(summaryReport, selectionResult, downloadResult, scriptOutput);
            log.info("Vodafone sync-fix task completed. Reports written to {}", runDirectory.toAbsolutePath());
        } catch (Exception e) {
            log.error("Vodafone sync-fix task failed", e);
            throw new RuntimeException("Vodafone sync-fix task failed", e);
        } finally {
            if (cleanupDownloads) {
                deleteDirectory(downloadDirectory);
            }
        }
    }

    private Path createRunDirectory() {
        Path runDirectory = Path.of(workingDirectory, LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        try {
            Files.createDirectories(runDirectory);
            return runDirectory;
        } catch (IOException e) {
            throw new RuntimeException("Unable to create working directory " + runDirectory.toAbsolutePath(), e);
        }
    }

    private SelectionResult collectPreferredMp4Files(List<String> xmlBlobNames) {
        Map<String, SelectedMp4File> preferredMp4Files = new LinkedHashMap<>();
        int xmlBlobsScanned = 0;

        for (String xmlBlobName : xmlBlobNames) {
            if (hasReachedFileLimit(preferredMp4Files.size())) {
                log.info("Reached configured file limit of {}, stopping XML traversal", fileLimit);
                return new SelectionResult(preferredMp4Files, xmlBlobsScanned, true);
            }

            xmlBlobsScanned++;
            InputStreamResource blobResource = azureVodafoneStorageService.fetchSingleXmlBlob(containerName, xmlBlobName);
            if (blobResource == null) {
                log.warn("Unable to fetch XML blob {}", xmlBlobName);
                continue;
            }

            try (InputStream xmlStream = blobResource.getInputStream()) {
                String blobPrefix = xmlBlobName.contains("/") ? xmlBlobName.substring(0, xmlBlobName.indexOf('/')) : "";
                for (VodafonePreferredMp4Metadata metadata
                    : vodafonePreferredMp4XmlParser.parsePreferredMp4Files(xmlStream, blobPrefix, xmlBlobName)) {
                    SelectedMp4File selectedMp4File = new SelectedMp4File(
                        metadata.xmlBlobName(),
                        metadata.archiveId(),
                        metadata.blobPath(),
                        1
                    );
                    preferredMp4Files.merge(
                        selectedMp4File.blobPath(),
                        selectedMp4File,
                        (existing, ignored) -> existing.incrementDuplicateCount()
                    );
                    if (hasReachedFileLimit(preferredMp4Files.size())) {
                        log.info("Reached configured file limit of {}, stopping XML traversal", fileLimit);
                        return new SelectionResult(preferredMp4Files, xmlBlobsScanned, true);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse XML blob {}", xmlBlobName, e);
            }
        }

        return new SelectionResult(preferredMp4Files, xmlBlobsScanned, false);
    }

    private DownloadResult downloadSelectedMp4Files(Path downloadDirectory, Map<String, SelectedMp4File> selectedMp4Files) {
        List<DownloadedMp4File> downloadedFiles = new ArrayList<>();
        List<DownloadedMp4File> failedFiles = new ArrayList<>();

        for (SelectedMp4File selectedMp4File : selectedMp4Files.values()) {
            Path localPath = resolveDownloadPath(downloadDirectory, selectedMp4File.blobPath());
            try {
                Files.createDirectories(localPath.getParent());
                boolean downloaded = azureVodafoneStorageService.downloadBlob(
                    containerName,
                    selectedMp4File.blobPath(),
                    localPath.toString()
                );

                DownloadedMp4File downloadedMp4File = new DownloadedMp4File(
                    selectedMp4File,
                    localPath,
                    downloaded ? "downloaded" : "download_failed"
                );

                if (downloaded) {
                    downloadedFiles.add(downloadedMp4File);
                } else {
                    failedFiles.add(downloadedMp4File);
                }
            } catch (Exception e) {
                log.error("Failed to download blob {}", selectedMp4File.blobPath(), e);
                failedFiles.add(new DownloadedMp4File(selectedMp4File, localPath, "download_failed"));
            }
        }

        return new DownloadResult(downloadedFiles, failedFiles);
    }

    private Path resolveDownloadPath(Path downloadDirectory, String blobPath) {
        Path resolvedPath = downloadDirectory.resolve(blobPath).normalize();
        if (!resolvedPath.startsWith(downloadDirectory.normalize())) {
            throw new IllegalArgumentException("Resolved download path escapes working directory: " + blobPath);
        }
        return resolvedPath;
    }

    private void writeSelectionReport(Path selectionReport, DownloadResult downloadResult) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(selectionReport, StandardCharsets.UTF_8)) {
            writer.write("xml_blob,archive_id,blob_path,duplicate_count,local_file,status");
            writer.newLine();

            for (DownloadedMp4File file : downloadResult.downloadedFiles()) {
                writeSelectionLine(writer, file);
            }
            for (DownloadedMp4File file : downloadResult.failedFiles()) {
                writeSelectionLine(writer, file);
            }
        }
    }

    private void writeSelectionLine(BufferedWriter writer, DownloadedMp4File file) throws IOException {
        writer.write(String.join(",",
            csvEscape(file.selectedMp4File().xmlBlobName()),
            csvEscape(file.selectedMp4File().archiveId()),
            csvEscape(file.selectedMp4File().blobPath()),
            csvEscape(String.valueOf(file.selectedMp4File().duplicateCount())),
            csvEscape(file.localPath().toString()),
            csvEscape(file.status())
        ));
        writer.newLine();
    }

    private String runSyncScript(Path downloadDirectory, Path scriptReport) {
        Path resolvedScriptPath = Path.of(scriptPath).toAbsolutePath().normalize();
        CommandLine commandLine = new CommandLine("bash")
            .addArgument(resolvedScriptPath.toString(), true)
            .addArgument(downloadDirectory.toAbsolutePath().toString(), true)
            .addArgument(scriptReport.toAbsolutePath().toString(), true);

        if (dryRun) {
            commandLine.addArgument("--dry-run");
        }

        String commandOutput = commandExecutor.executeAndGetOutput(commandLine);
        return Optional.ofNullable(commandOutput).orElse("Script execution failed; no output captured.");
    }

    private void writeSummaryReport(Path summaryReport,
                                    SelectionResult selectionResult,
                                    DownloadResult downloadResult,
                                    String scriptOutput) throws IOException {
        List<String> lines = List.of(
            "Container: " + containerName,
            "Dry run: " + dryRun,
            "File limit: " + (fileLimit > 0 ? fileLimit : "unlimited"),
            "File limit reached: " + selectionResult.limitReached(),
            "XML blobs scanned: " + selectionResult.xmlBlobsScanned(),
            "Unique preferred MP4 blobs: " + selectionResult.selectedMp4Files().size(),
            "Downloaded successfully: " + downloadResult.downloadedFiles().size(),
            "Download failures: " + downloadResult.failedFiles().size(),
            "",
            "Script output:",
            scriptOutput
        );

        Files.writeString(summaryReport, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to clean up {}", directory, e);
        }
    }

    private boolean hasReachedFileLimit(int selectedFileCount) {
        return fileLimit > 0 && selectedFileCount >= fileLimit;
    }

    private String csvEscape(String value) {
        String sanitized = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + sanitized + "\"";
    }

    private record SelectedMp4File(String xmlBlobName, String archiveId, String blobPath, int duplicateCount) {
        private SelectedMp4File incrementDuplicateCount() {
            return new SelectedMp4File(xmlBlobName, archiveId, blobPath, duplicateCount + 1);
        }
    }

    private record DownloadedMp4File(SelectedMp4File selectedMp4File, Path localPath, String status) {
    }

    private record DownloadResult(List<DownloadedMp4File> downloadedFiles, List<DownloadedMp4File> failedFiles) {
    }

    private record SelectionResult(Map<String, SelectedMp4File> selectedMp4Files,
                                   int xmlBlobsScanned,
                                   boolean limitReached) {
    }
}
