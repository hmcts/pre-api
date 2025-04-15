package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { LoggingService.class })
public class LoggingServiceTest {
    @Autowired
    private LoggingService loggingService;

    private static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/Migration Reports/output.log";

    @BeforeEach
    void setUp() {
        loggingService = new LoggingService();
        // Ensure clean log before each test
        File logFile = new File(LOG_FILE_PATH);
        logFile.getParentFile().mkdirs();
        if (logFile.exists()) {
            logFile.delete();
        }
        loggingService.setDebugEnabled(false);
    }

    @Test
    @DisplayName("Should successfully initialise file with header")
    void initialiseLogFileCreatesFileWithHeader() throws IOException {
        loggingService.initializeLogFile(MigrationType.DELTA);

        List<String> lines = getFileLines();
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(1)).contains("Vodafone ETL Job Started");
    }

    @Test
    @DisplayName("Successfully log info and write to log file")
    void logInfo() throws IOException {
        loggingService.logInfo("Test message %s", "123");

        List<String> lines = getFileLines();
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).contains("INFO");
        assertThat(lines.getFirst()).contains("Test message 123");
    }

    @Test
    @DisplayName("Successfully log warn and write to log file")
    void logWarn() throws IOException {
        loggingService.logWarning("Test message %s", "123");

        List<String> lines = getFileLines();
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).contains("WARN");
        assertThat(lines.getFirst()).contains("Test message 123");
    }

    @Test
    @DisplayName("Successfully log error and write to log file")
    void logError() throws IOException {
        loggingService.logError("Test message %s", "123");

        List<String> lines = getFileLines();
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).contains("ERROR");
        assertThat(lines.getFirst()).contains("Test message 123");
    }

    @Test
    @DisplayName("Successfully log debug and write to log file")
    void logDebugWhenDebugEnabled() throws IOException {
        loggingService.setDebugEnabled(true);
        loggingService.logDebug("Test message %s", "123");

        assertThat(doesFileExist()).isTrue();
        List<String> lines = getFileLines();
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).contains("INFO");
        assertThat(lines.getFirst()).contains("Debug logging enabled");

        assertThat(lines.getLast()).contains("DEBUG");
        assertThat(lines.getLast()).contains("Test message 123");
    }

    @Test
    @DisplayName("Successfully ignore debug when debug not enabled")
    void logDebugWhenDebugDisabled() {
        loggingService.logDebug("Test message %s", "123");

        assertThat(doesFileExist()).isFalse();
    }

    @Test
    @DisplayName("Should update total failed updates correctly")
    void setTotalFailedUpdatesCorrectly() throws IOException {
        Map<String, List<FailedItem>> failures = new HashMap<>();
        failures.put("Category1", Arrays.asList(new FailedItem(), new FailedItem()));
        List<TestItem> testItems = Collections.singletonList(new TestItem());

        loggingService.setTotalFailed(failures, testItems);

        loggingService.setTotalRecords(10);
        loggingService.setTotalMigrated(5);
        loggingService.setTotalInvited(2);
        loggingService.logSummary();

        List<String> lines = getFileLines();
        assertThat(lines).isNotEmpty();
        Optional<String> expectedLine = lines.stream()
            .filter(line -> line.contains("Total Failed Items"))
            .findFirst();
        assertThat(expectedLine).isPresent();
        assertThat(expectedLine.get())
            .contains("Total Failed Items")
            .contains("3");
    }

    private List<String> getFileLines() throws IOException {
        return Files.readAllLines(Paths.get(LOG_FILE_PATH));
    }

    private boolean doesFileExist() {
        return Files.exists(Paths.get(LOG_FILE_PATH));
    }
}
