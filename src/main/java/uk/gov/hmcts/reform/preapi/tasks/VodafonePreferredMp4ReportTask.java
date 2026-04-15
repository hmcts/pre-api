package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4Metadata;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4XmlParser;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class VodafonePreferredMp4ReportTask extends RobotUserTask {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final VodafonePreferredMp4XmlParser vodafonePreferredMp4XmlParser;

    @Value("${tasks.vodafone-preferred-mp4-report.container:${tasks.batch-import-missing-mk-assets.mp4-source-container}}")
    private String containerName;

    @Value("${tasks.vodafone-preferred-mp4-report.working-directory:build/reports/vf-preferred-mp4-report}")
    private String workingDirectory;

    @Value("${tasks.vodafone-preferred-mp4-report.file-limit:0}")
    private int fileLimit;

    @Autowired
    public VodafonePreferredMp4ReportTask(UserService userService,
                                          UserAuthenticationService userAuthenticationService,
                                          @Value("${vodafone-user-email}") String cronUserEmail,
                                          AzureVodafoneStorageService azureVodafoneStorageService,
                                          VodafonePreferredMp4XmlParser vodafonePreferredMp4XmlParser) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.vodafonePreferredMp4XmlParser = vodafonePreferredMp4XmlParser;
    }

    @Override
    public void run() {
        log.info("Starting Vodafone preferred MP4 report task for container {}", containerName);
        signInRobotUser();

        Path runDirectory = createRunDirectory();
        Path reportFile = runDirectory.resolve("preferred-mp4s.csv");
        Path summaryFile = runDirectory.resolve("summary.txt");

        try {
            SelectionResult selectionResult =
                collectPreferredMp4Files(azureVodafoneStorageService.fetchBlobNames(containerName));

            writeReport(reportFile, selectionResult.selectedMp4Files());
            writeSummary(summaryFile, selectionResult);

            log.info("Vodafone preferred MP4 report task completed. Reports written to {}", runDirectory.toAbsolutePath());
        } catch (Exception e) {
            log.error("Vodafone preferred MP4 report task failed", e);
            throw new RuntimeException("Vodafone preferred MP4 report task failed", e);
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
        Map<String, ReportRow> preferredMp4Files = new LinkedHashMap<>();
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
                    ReportRow reportRow = new ReportRow(
                        metadata.xmlBlobName(),
                        metadata.archiveId(),
                        metadata.blobPath(),
                        metadata.fileName(),
                        containsSubstring(metadata.fileName(), "UGC"),
                        containsSubstring(metadata.fileName(), "EDITOR"),
                        1
                    );
                    preferredMp4Files.merge(
                        reportRow.blobPath(),
                        reportRow,
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

    private void writeReport(Path reportFile, Map<String, ReportRow> selectedMp4Files) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
            writer.write("xml_blob,archive_id,blob_path,file_name,duplicate_count,contains_ugc,contains_editor");
            writer.newLine();

            for (ReportRow row : selectedMp4Files.values()) {
                writer.write(String.join(",",
                    csvEscape(row.xmlBlobName()),
                    csvEscape(row.archiveId()),
                    csvEscape(row.blobPath()),
                    csvEscape(row.fileName()),
                    csvEscape(String.valueOf(row.duplicateCount())),
                    csvEscape(String.valueOf(row.containsUgc())),
                    csvEscape(String.valueOf(row.containsEditor()))
                ));
                writer.newLine();
            }
        }
    }

    private void writeSummary(Path summaryFile, SelectionResult selectionResult) throws IOException {
        long ugcCount = selectionResult.selectedMp4Files().values().stream().filter(ReportRow::containsUgc).count();
        long editorCount = selectionResult.selectedMp4Files().values().stream().filter(ReportRow::containsEditor).count();

        List<String> lines = List.of(
            "Container: " + containerName,
            "File limit: " + (fileLimit > 0 ? fileLimit : "unlimited"),
            "File limit reached: " + selectionResult.limitReached(),
            "XML blobs scanned: " + selectionResult.xmlBlobsScanned(),
            "Unique preferred MP4 blobs: " + selectionResult.selectedMp4Files().size(),
            "Preferred MP4s containing UGC: " + ugcCount,
            "Preferred MP4s containing Editor: " + editorCount
        );

        Files.writeString(summaryFile, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
    }

    private boolean hasReachedFileLimit(int selectedFileCount) {
        return fileLimit > 0 && selectedFileCount >= fileLimit;
    }

    private boolean containsSubstring(String fileName, String substring) {
        return fileName != null && fileName.toUpperCase(Locale.UK).contains(substring);
    }

    private String csvEscape(String value) {
        String sanitized = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + sanitized + "\"";
    }

    private record ReportRow(String xmlBlobName,
                             String archiveId,
                             String blobPath,
                             String fileName,
                             boolean containsUgc,
                             boolean containsEditor,
                             int duplicateCount) {
        private ReportRow incrementDuplicateCount() {
            return new ReportRow(
                xmlBlobName,
                archiveId,
                blobPath,
                fileName,
                containsUgc,
                containsEditor,
                duplicateCount + 1
            );
        }
    }

    private record SelectionResult(Map<String, ReportRow> selectedMp4Files,
                                   int xmlBlobsScanned,
                                   boolean limitReached) {
    }
}
