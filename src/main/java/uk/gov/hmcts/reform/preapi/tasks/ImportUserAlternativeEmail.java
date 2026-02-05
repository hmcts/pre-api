package uk.gov.hmcts.reform.preapi.tasks;

import com.microsoft.graph.models.ObjectIdentity;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.B2CGraphService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class ImportUserAlternativeEmail extends RobotUserTask {

    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final B2CGraphService b2cGraphService;

    @Value("${azure.vodafoneStorage.csvContainer}")
    private String containerName;

    @Value("${USE_LOCAL_CSV:false}")
    private boolean useLocalCsv;

    private static final String CSV_BLOB_PATH = "alternative_email_import.csv";
    private static final String LOCAL_CSV_PATH = "src/main/resources/batch/alternative_email_import.csv";
    private static final String REPORT_OUTPUT_DIR = "Migration Reports";
    private static final String STATUS_ERROR = "ERROR";

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
                                AzureVodafoneStorageService azureVodafoneStorageService,
                                B2CGraphService b2cGraphService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.b2cGraphService = b2cGraphService;
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
        } catch (IOException e) {
            log.error("Failed to read CSV file for user alternative email import", e);
            throw new IllegalStateException("Failed to import user alternative email data: CSV read error", e);
        } catch (IllegalStateException e) {
            log.error("Failed to import user alternative email data", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during user alternative email import", e);
            throw new IllegalStateException("Failed to import user alternative email data: Unexpected error", e);
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

            if (!resource.exists()) { // NOSONAR
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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(),
                                                                              StandardCharsets.UTF_8))) {
            try {
                return new CsvToBeanBuilder<ImportRow>(reader)
                    .withType(ImportRow.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
            } catch (RuntimeException e) {
                if (e.getCause() instanceof CsvRequiredFieldEmptyException) {
                    throw new IOException("CSV header invalid: " + e.getCause().getMessage(), e);
                }
                throw e;
            }
        }
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private List<ImportResult> processImports(List<ImportRow> importRows) {
        List<ImportResult> results = new ArrayList<>();
        int successCount = 0;
        int notFoundCount = 0;
        int emptyAltEmailCount = 0;
        int errorCount = 0;

        for (ImportRow row : importRows) {
            try {
                ImportResult result = processRow(row);

                results.add(result);
                switch (result.getStatus()) {
                    case "SUCCESS" -> successCount++;
                    case "NOT_FOUND" -> notFoundCount++;
                    case "SKIPPED" -> emptyAltEmailCount++;
                    case STATUS_ERROR -> errorCount++;
                    default -> log.warn("Unknown status: {}", result.getStatus()); // NOSONAR
                }
            } catch (Exception e) {
                log.error("Error processing row for email: {}", row.getEmail(), e);
                results.add(new ImportResult(
                    row.getEmail(),
                    row.getAlternativeEmail() != null ? row.getAlternativeEmail() : "",
                    STATUS_ERROR,
                    "Error: " + e.getMessage()
                ));
                errorCount++;
            }
        }

        log.info("Import summary - Success: {}, Not Found: {}, Empty Email2: {}, Errors: {}",
            successCount, notFoundCount, emptyAltEmailCount, errorCount);

        return results;
    }

    private ImportResult processRow(ImportRow row) {
        if (row.getAlternativeEmail() == null || row.getAlternativeEmail().trim().isEmpty()) {
            return new ImportResult(
                row.getEmail(),
                "",
                "SKIPPED",
                "Alternative email is empty"
            );
        }

        Optional<User> userOpt = userService.findByOriginalEmail(row.getEmail());

        if (userOpt.isEmpty()) {
            return new ImportResult(
                row.getEmail(),
                row.getAlternativeEmail(),
                "NOT_FOUND",
                "User with email not found"
            );
        }

        User user = userOpt.get();

        // Check if alternative email already exists for another user
        Optional<User> existingAltUserEmail = userService
            .findByAlternativeEmail(row.getAlternativeEmail());

        if (existingAltUserEmail.isPresent() && !existingAltUserEmail.get().getId().equals(user.getId())) {
            return new ImportResult(
                row.getEmail(),
                row.getAlternativeEmail(),
                STATUS_ERROR,
                "Alternative email already exists for another user: " + existingAltUserEmail.get().getEmail()
            );
        }

        // Update B2C first
        ImportResult result = updateB2CAlternativeEmail(row);

        // Update local database regardless of whether B2C succeeds
        try {
            userService.updateAlternativeEmail(user.getId(), row.getAlternativeEmail());
        } catch (Exception e) {
            log.error("Failed to update local DB for user {}: {}", row.getEmail(), e.getMessage(), e);
            if (Objects.equals(result.getStatus(), STATUS_ERROR)) {
                return new ImportResult(
                    row.getEmail(),
                    row.getAlternativeEmail(),
                    STATUS_ERROR,
                    "Failed to update local DB: " + e.getMessage() + result.getMessage()
                );
            }
            return new ImportResult(
                row.getEmail(),
                row.getAlternativeEmail(),
                STATUS_ERROR,
                "Failed to update local DB: " + e.getMessage()
            );
        }

        return result;
    }

    private ImportResult updateB2CAlternativeEmail(ImportRow row) {
        try {
            Optional<com.microsoft.graph.models.User> maybeB2cUser =
                b2cGraphService.findUserByPrimaryEmail(row.getEmail());

            if (maybeB2cUser.isEmpty()) {
                return new ImportResult(
                    row.getEmail(),
                    row.getAlternativeEmail(),
                    STATUS_ERROR,
                    "User not found in B2C with email: " + row.getEmail()
                );
            }

            com.microsoft.graph.models.User b2cUser = maybeB2cUser.get();
            List<ObjectIdentity> identities = b2cUser.getIdentities();

            if (identities == null) {
                identities = new ArrayList<>();
            }

            // Check if alternative email already exists as an identity
            boolean alternativeEmailExists = identities.stream()
                .anyMatch(identity -> identity.getIssuerAssignedId() != null
                    && identity.getIssuerAssignedId().equalsIgnoreCase(row.getAlternativeEmail()));

            if (!alternativeEmailExists) {
                // Create new identity for alternative email
                ObjectIdentity newIdentity = new ObjectIdentity();
                newIdentity.setSignInType("emailAddress");
                newIdentity.setIssuer(identities.isEmpty() ? "unknown" : identities.get(0).getIssuer());
                newIdentity.setIssuerAssignedId(row.getAlternativeEmail());

                List<ObjectIdentity> updatedIdentities = new ArrayList<>(identities);
                updatedIdentities.add(newIdentity);

                b2cGraphService.updateUserIdentities(b2cUser.getId(), updatedIdentities);
                log.info(
                    "Added alternative email {} as identity to B2C user {}",
                    row.getAlternativeEmail(), row.getEmail()
                );
            } else {
                log.info(
                    "Alternative email {} already exists as identity for B2C user {}",
                    row.getAlternativeEmail(), row.getEmail()
                );
            }
        } catch (Exception e) {
            log.error("Failed to update B2C identity for user {}: {}", row.getEmail(), e.getMessage(), e);
            return new ImportResult(
                row.getEmail(),
                row.getAlternativeEmail(),
                STATUS_ERROR,
                "Failed to update B2C identity: " + e.getMessage()
            );
        }
        return new ImportResult(
            row.getEmail(),
            row.getAlternativeEmail(),
            "SUCCESS",
            "Alternative email updated successfully in both local DB and B2C"
        );
    }

    private void generateReport(List<ImportResult> results) {
        try {
            List<String> headers = List.of("email", "alternativeEmail", "status", "message");
            List<List<String>> rows = results.stream()
                .map(ImportResult::toRow)
                .toList();

            Path reportPath = ReportCsvWriter.writeToCsv(headers, rows,
                "Alternative_Email_Report", REPORT_OUTPUT_DIR, true);
            log.info("Report generated successfully in {}", REPORT_OUTPUT_DIR);

            if (reportPath != null) {
                File reportFile = reportPath.toFile();
                if (reportFile.exists()) {
                    String fileName = reportPath.getFileName().toString();
                    String blobPath = "reports/" + fileName;
                    azureVodafoneStorageService.uploadCsvFile(containerName, blobPath, reportFile);
                    log.info("Report uploaded to Azure: {}/{}", containerName, blobPath);
                }
            }
        } catch (IOException e) {
            log.error("Failed to generate report", e);
        }
    }
}
