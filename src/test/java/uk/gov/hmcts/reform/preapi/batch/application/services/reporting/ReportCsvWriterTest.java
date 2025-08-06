package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReportCsvWriterTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeToCsvSuccess() throws IOException {
        List<String> headers = List.of("ID", "Name", "Age");
        List<List<String>> dataRows = List.of(
            List.of("1", "Alice", "30"),
            List.of("2", "Bob", "25")
        );
        String fileNamePrefix = "report";
        boolean showTimestamp = false;

        Path filePath = ReportCsvWriter.writeToCsv(
            headers,
            dataRows,
            fileNamePrefix,
            tempDir.toString(),
            showTimestamp
        );
        assertThat(Files.exists(filePath)).isTrue();
        List<String> lines = Files.readAllLines(filePath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("ID,Name,Age");
        assertThat(lines.get(1)).isEqualTo("1,Alice,30");
        assertThat(lines.get(2)).isEqualTo("2,Bob,25");
    }

    @Test
    void writeToCsvSuccessWithTimestamp() throws IOException {
        List<String> headers = List.of("ID", "Name");
        List<List<String>> dataRows = List.of(
            List.of("3", "Charlie")
        );
        String fileNamePrefix = "timestamped_report";
        boolean showTimestamp = true;

        Path filePath = ReportCsvWriter.writeToCsv(
            headers,
            dataRows,
            fileNamePrefix,
            tempDir.toString(),
            showTimestamp
        );
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(filePath.getFileName().toString()
                       .matches("timestamped_report-\\d{4}-\\d{2}-\\d{2}-\\d{2}:\\d{2}\\.csv"))
            .isTrue();
    }

    @Test
    void writeToCsvEmptyHeadersError() {
        List<String> headers = List.of();
        List<List<String>> dataRows = List.of(List.of("1", "Alice"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ReportCsvWriter.writeToCsv(headers, dataRows, "report", tempDir.toString(), false)
        );
    }

    @Test
    void writeToCsvNullDataRowsError() {
        List<String> headers = List.of("ID", "Name");
        assertThrows(IllegalArgumentException.class, () ->
            ReportCsvWriter.writeToCsv(headers, null, "report", tempDir.toString(), false)
        );
    }

    @Test
    void writeToCsvInvalidOutputDirectoryError() {
        List<String> headers = List.of("ID", "Name");
        List<List<String>> dataRows = List.of(List.of("1", "Alice"));
        assertThrows(Exception.class, () ->
            ReportCsvWriter.writeToCsv(headers, dataRows, "report", "//invalid/path", false)
        );
    }

    @Test
    void writeToCsvEmptyFilenamePrefixError() {
        List<String> headers = List.of("ID", "Name");
        List<List<String>> dataRows = List.of(List.of("1", "Alice"));
        assertThrows(IllegalArgumentException.class, () ->
            ReportCsvWriter.writeToCsv(headers, dataRows, "", tempDir.toString(), false)
        );
    }
}
