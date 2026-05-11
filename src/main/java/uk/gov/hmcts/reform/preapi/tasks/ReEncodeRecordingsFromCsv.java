package uk.gov.hmcts.reform.preapi.tasks;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReEncodeRecordingsFromCsv extends RobotUserTask {

    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_ERROR = "ERROR";

    private final EditRequestService editRequestService;
    private final String csvPath;
    private final boolean forceReencode;

    private static final class ReEncodeSummary {
        private int submittedCount;
        private int skippedCount;
        private int errorCount;

        void countSubmitted() {
            submittedCount++;
        }

        void countSkipped() {
            skippedCount++;
        }

        void countError() {
            errorCount++;
        }
    }

    @Data
    public static class ReEncodeRow {
        @CsvBindByName(column = "source_recording_id")
        private String sourceRecordingId;

        @CsvBindByName(column = "case_reference")
        private String caseReference;
    }

    public ReEncodeRecordingsFromCsv(EditRequestService editRequestService,
                                     UserService userService,
                                     UserAuthenticationService userAuthenticationService,
                                     @Value("${cron-user-email}") String cronUserEmail,
                                     @Value("${REENCODE_RECORDINGS_CSV_PATH:}") String csvPath,
                                     @Value("${REENCODE_RECORDINGS_FORCE:false}") boolean forceReencode) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.editRequestService = editRequestService;
        this.csvPath = csvPath;
        this.forceReencode = forceReencode;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Starting ReEncodeRecordingsFromCsv task");

        try {
            List<ReEncodeRow> rows = readCsvFile();
            processRows(rows);
        } catch (IOException e) {
            log.error("Failed to read re-encode CSV file", e);
            throw new IllegalStateException("Failed to read re-encode CSV file", e);
        }
    }

    private List<ReEncodeRow> readCsvFile() throws IOException {
        if (csvPath == null || csvPath.isBlank()) {
            throw new IOException("REENCODE_RECORDINGS_CSV_PATH must be set");
        }

        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            throw new IOException("CSV file not found: " + csvPath);
        }

        log.info("Reading re-encode CSV file from {}", path.toAbsolutePath());
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CsvToBean<ReEncodeRow> csvToBean = new CsvToBeanBuilder<ReEncodeRow>(reader)
                .withType(ReEncodeRow.class)
                .withIgnoreLeadingWhiteSpace(true)
                .withIgnoreEmptyLine(true)
                .withThrowExceptions(false)
                .build();
            List<ReEncodeRow> rows = csvToBean.parse();
            csvToBean.getCapturedExceptions().forEach(exception -> log.error(
                "Skipping CSV row {} because it could not be parsed: {}",
                exception.getLineNumber(), exception.getMessage()
            ));
            return rows;
        }
    }

    private void processRows(List<ReEncodeRow> rows) {
        Set<UUID> existingReencodeRecordingIds = forceReencode ? Set.of() : findExistingReencodeRecordingIds(rows);
        Set<UUID> seenRecordingIds = new HashSet<>();
        ReEncodeSummary summary = new ReEncodeSummary();

        log.info(
            "Processing {} re-encode CSV rows. Existing re-encode check enabled: {}",
            rows.size(), !forceReencode
        );

        for (int i = 0; i < rows.size(); i++) {
            String status = processRow(rows.get(i), i + 1, seenRecordingIds, existingReencodeRecordingIds);
            switch (status) {
                case STATUS_SUBMITTED -> summary.countSubmitted();
                case STATUS_SKIPPED -> summary.countSkipped();
                case STATUS_ERROR -> summary.countError();
                default -> log.warn("Unknown re-encode row status: {}", status);
            }
        }

        log.info(
            "Completed ReEncodeRecordingsFromCsv task. Submitted: {}, Skipped: {}, Errors: {}",
            summary.submittedCount, summary.skippedCount, summary.errorCount
        );
    }

    private Set<UUID> findExistingReencodeRecordingIds(List<ReEncodeRow> rows) {
        Set<UUID> recordingIds = rows.stream()
            .map(this::parseRecordingId)
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());

        return editRequestService.findRecordingIdsWithForceReencodeRequests(recordingIds);
    }

    private String processRow(ReEncodeRow row,
                              int rowNumber,
                              Set<UUID> seenRecordingIds,
                              Set<UUID> existingReencodeRecordingIds) {
        Optional<UUID> maybeRecordingId = parseRecordingId(row);
        if (maybeRecordingId.isEmpty()) {
            log.error("Skipping row {} because source_recording_id is not a valid UUID", rowNumber);
            return STATUS_ERROR;
        }

        UUID recordingId = maybeRecordingId.get();
        String caseReference = describe(row);
        if (!seenRecordingIds.add(recordingId)) {
            log.info("Skipping row {} for recording {} ({}): duplicate row in this CSV", rowNumber, recordingId,
                     caseReference);
            return STATUS_SKIPPED;
        }

        if (existingReencodeRecordingIds.contains(recordingId)) {
            log.info("Skipping row {} for recording {} ({}): re-encode request already exists", rowNumber,
                     recordingId, caseReference);
            return STATUS_SKIPPED;
        }

        try {
            CreateEditRequestDTO dto = createReencodeRequest(recordingId);
            editRequestService.upsert(dto);
            log.info("Submitted re-encode request {} for recording {} ({}) from row {}", dto.getId(), recordingId,
                     caseReference, rowNumber);
            return STATUS_SUBMITTED;
        } catch (Exception e) {
            log.error("Failed to submit re-encode request for recording {} ({}) from row {}", recordingId,
                      caseReference, rowNumber, e);
            return STATUS_ERROR;
        }
    }

    private Optional<UUID> parseRecordingId(ReEncodeRow row) {
        if (row.getSourceRecordingId() == null || row.getSourceRecordingId().isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(row.getSourceRecordingId().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private CreateEditRequestDTO createReencodeRequest(UUID recordingId) {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(recordingId);
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(List.of());
        dto.setForceReencode(true);
        dto.setSendNotifications(false);
        return dto;
    }

    private String describe(ReEncodeRow row) {
        if (row.getCaseReference() == null || row.getCaseReference().isBlank()) {
            return "no case reference";
        }

        return row.getCaseReference().trim();
    }
}
