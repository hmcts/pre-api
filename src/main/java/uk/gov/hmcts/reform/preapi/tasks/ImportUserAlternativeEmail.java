package uk.gov.hmcts.reform.preapi.tasks;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ImportUserAlternativeEmail extends RobotUserTask {

    private final AzureVodafoneStorageService azureVodafoneStorageService;

    @Value("${azure.vodafoneStorage.csvContainer}")
    private String containerName;

    @Value("${USE_LOCAL_CSV:false}")
    private boolean useLocalCsv;

    private static final String CSV_BLOB_PATH = "alternative_email_import.csv";
    private static final String LOCAL_CSV_PATH = "src/main/resources/batch/alternative_email_import.csv";
    private static final String REPORT_OUTPUT_DIR = "Migration Reports";

    @Data
    public static class ImportRow {
        @CsvBindByName(column = "email", required = true)
        private String email;

        @CsvBindByName(column = "alternativeEmail")
        private String alternativeEmail;
    }

    @Data
    public static class ImportResult {
        private String email;
        private String alternativeEmail;
        private String status;
        private String message;

        public ImportResult(String email, String alternativeEmail, String status, String message) {
            this.email = email;
            this.alternativeEmail = alternativeEmail;
            this.status = status;
            this.message = message;
        }

        public List<String> toRow() {
            return List.of(email, alternativeEmail != null ? alternativeEmail : "", status, message);
        }
    }

    @Autowired
    public ImportUserAlternativeEmail(UserService userService,
                                UserAuthenticationService userAuthenticationService,
                                @Value("${cron-user-email}") String cronUserEmail,
                                AzureVodafoneStorageService azureVodafoneStorageService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.azureVodafoneStorageService = azureVodafoneStorageService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Starting ImportUserAlternativeEmail Task");

        try {
            List<ImportRow> importRows = readCsvFile();
            List<ImportResult> results = processImports(importRows);
            generateReport(results);
            log.info("Completed ImportUserAlternativeEmail Task. Processed {} rows", importRows.size());
        } catch (Exception e) {
            log.error("Failed to import user alternative email data", e);
            throw new RuntimeException("Failed to import user alternative email data", e);
        }
    }

    private List<ImportRow> readCsvFile() throws IOException {
        Resource resource;
        
        if (useLocalCsv) {
            log.info("Reading CSV from local file: {}", LOCAL_CSV_PATH);
            resource = new FileSystemResource(LOCAL_CSV_PATH);
            
            if (!resource.exists()) {
                resource = new ClassPathResource("batch/alternative_email_import.csv");
            }
            
            if (!resource.exists()) {
                throw new IOException("CSV file not found at local path: " + LOCAL_CSV_PATH);
            }
        } else {
            log.info("Reading CSV from Azure blob: {}/{}", containerName, CSV_BLOB_PATH);
            InputStreamResource blobResource = azureVodafoneStorageService
                .fetchSingleXmlBlob(containerName, CSV_BLOB_PATH);
            
            if (blobResource == null) {
                throw new IOException("CSV file not found in Azure: " + containerName + "/" + CSV_BLOB_PATH);
            }
            resource = blobResource;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            return new CsvToBeanBuilder<ImportRow>(reader)
                .withType(ImportRow.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build()
                .parse();
        }
    }

    @Transactional
    private List<ImportResult> processImports(List<ImportRow> importRows) {
        List<ImportResult> results = new ArrayList<>();
        int successCount = 0;
        int notFoundCount = 0;
        int emptyAltEmailCount = 0;
        int errorCount = 0;

        for (ImportRow row : importRows) {
            try {
                if (row.getAlternativeEmail() == null || row.getAlternativeEmail().trim().isEmpty()) {
                    results.add(new ImportResult(
                        row.getEmail(),
                        "",
                        "SKIPPED",
                        "Alternative email is empty"
                    ));
                    emptyAltEmailCount++;
                    continue;
                }

                Optional<User> userOpt = userService.findByOriginalEmail(row.getEmail());

                if (userOpt.isEmpty()) {
                    results.add(new ImportResult(
                        row.getEmail(),
                        row.getAlternativeEmail(),
                        "NOT_FOUND",
                        "User with email not found"
                    ));
                    notFoundCount++;
                    continue;
                }

                User user = userOpt.get();

                Optional<User> existingAltUserEmail = userService
                    .findByAlternativeEmail(row.getAlternativeEmail());
                
                if (existingAltUserEmail.isPresent() && !existingAltUserEmail.get().getId().equals(user.getId())) {
                    results.add(new ImportResult(
                        row.getEmail(),
                        row.getAlternativeEmail(),
                        "ERROR",
                        "Alternative email already exists for another user: " + existingAltUserEmail.get().getEmail()
                    ));
                    errorCount++;
                    continue;
                }

                userService.updateAlternativeEmail(user.getId(), row.getAlternativeEmail());

                results.add(new ImportResult(
                    row.getEmail(),
                    row.getAlternativeEmail(),
                    "SUCCESS",
                    "Alternative email updated successfully"
                ));
                successCount++;

            } catch (Exception e) {
                log.error("Error processing row for email: {}", row.getEmail(), e);
                results.add(new ImportResult(
                    row.getEmail(),
                    row.getAlternativeEmail() != null ? row.getAlternativeEmail() : "",
                    "ERROR",
                    "Error: " + e.getMessage()
                ));
                errorCount++;
            }
        }

        log.info("Import summary - Success: {}, Not Found: {}, Empty Email2: {}, Errors: {}",
            successCount, notFoundCount, emptyAltEmailCount, errorCount);

        return results;
    }

    private void generateReport(List<ImportResult> results) {
        try {
            List<String> headers = List.of("email", "alternativeEmail", "status", "message");
            List<List<String>> rows = results.stream()
                .map(ImportResult::toRow)
                .toList();

            ReportCsvWriter.writeToCsv(headers, rows, "Alternative_Email_Report", REPORT_OUTPUT_DIR, true);
            log.info("Report generated successfully in {}", REPORT_OUTPUT_DIR);
        } catch (IOException e) {
            log.error("Failed to generate report", e);
        }
    }
}
