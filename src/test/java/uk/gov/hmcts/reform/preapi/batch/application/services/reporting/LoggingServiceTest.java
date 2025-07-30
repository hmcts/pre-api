package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoggingServiceTest {
    private LoggingService loggingService;
    private static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/Migration Reports/output.log";

    @BeforeEach
    void setUp() {
        loggingService = new LoggingService();
        new File(LOG_FILE_PATH).delete();
        loggingService.initializeLogFile();
    }

    @Test
    @DisplayName("Should successfully initialise log file")
    void shouldInitialiseLogFile() throws IOException {
        File logFile = new File(LOG_FILE_PATH);
        assertTrue(logFile.exists());
        String content = Files.readString(logFile.toPath());
        assertTrue(content.contains("Vodafone ETL Job Started"));
    }

    @Test
    @DisplayName("Should add logs for warnings and errors to file")
    void shouldLogErrorAndWarning() throws IOException {
        loggingService.logError("Something went wrong: %d", 500);
        loggingService.logWarning("Careful: %s", "disk space low");

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertTrue(content.contains("ERROR"));
        assertTrue(content.contains("WARN"));
    }

    @Test
    @DisplayName("Should not log debug when disabled")
    void shouldNotLogDebugWhenDisabled() throws IOException {
        loggingService.setDebugEnabled(false);
        loggingService.logDebug("Should not be logged");
        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertFalse(content.contains("DEBUG"));
    }

    @Test
    @DisplayName("Should log debug when disabled")
    void shouldLogDebugWhenEnabled() throws IOException {
        loggingService.setDebugEnabled(true);
        loggingService.logDebug("Debug test: %d", 123);
        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertTrue(content.contains("DEBUG"));
    }

    @Test
    void shouldTrackAndDisplayProgress() {
        loggingService.setTotalRecords(10);
        for (int i = 0; i < 10; i++) {
            loggingService.incrementProgress();
        }
    }

    @Test
    @DisplayName("Should set total failures correctly")
    void shouldSetTotalFailedCorrectly() {
        Map<String, List<FailedItem>> categorizedFailures = Map.of(
            "Category1", List.of(new FailedItem(), new FailedItem()),
            "Category2", List.of(new FailedItem())
        );
        List<TestItem> testFailures = List.of(new TestItem(), new TestItem());

        loggingService.setTotalFailed(categorizedFailures, testFailures);

        int expectedTotalFailed = 5;
        assertEquals(expectedTotalFailed, loggingService.getTotalFailed());
        assertEquals(2, loggingService.failedCategoryCounts.get("Category1"));
        assertEquals(1, loggingService.failedCategoryCounts.get("Category2"));
    }

    @Test
    @DisplayName("Should generate summary correctly")
    void shouldLogSummaryCorrectly() throws IOException {
        loggingService.setTotalRecords(50);
        loggingService.setTotalMigrated(35);
        loggingService.setTotalFailed(Map.of(
            "TypeA", List.of(new FailedItem(), new FailedItem()),
            "TypeB", List.of(new FailedItem())
        ), List.of(new TestItem()));

        loggingService.logSummary();

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());

        assertTrue(content.contains("Total Records Processed"));
        assertTrue(content.contains("Total Migrated Items"));
        assertTrue(content.contains("Total Failed Items"));
        assertTrue(content.contains("BATCH SUMMARY"));
        assertTrue(content.contains("Total Execution Time"));

        assertTrue(content.contains("| Total Records Processed   |         50"));
        assertTrue(content.contains("| Total Migrated Items      |         35"));
        assertTrue(content.contains("| Total Failed Items        |          4"));
    }

    @Test
    @DisplayName("Should log warning if start time is not set")
    void shouldLogWarningWhenStartTimeNotSet() throws IOException {
        loggingService.initializeLogFile();
        loggingService.startTime = null;
        loggingService.logSummary();

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());

        assertTrue(content.contains("WARN"));
        assertTrue(content.contains("Start time was not set. Using current time as fallback."));
    }

    @Test
    @DisplayName("Should log execution time correctly")
    void shouldLogExecutionTimeCorrectly() throws IOException, InterruptedException {
        loggingService.setTotalRecords(1);
        loggingService.initializeLogFile();

        Thread.sleep(2000);

        loggingService.logSummary();

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertTrue(content.contains("| Total Execution Time      |          2 sec"));
    }

    @Test
    @DisplayName("Should handle no processed records gracefully")
    void shouldHandleNoProcessedRecords() throws IOException {
        loggingService.setTotalRecords(0);

        loggingService.logSummary();

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertTrue(content.contains("| Total Records Processed   |          0"));
        assertTrue(content.contains("| Total Migrated Items      |          0"));
        assertTrue(content.contains("| Total Failed Items        |          0"));
    }

    @Test
    @DisplayName("Should successfully log info")
    void shouldLogInfoWithCorrectFormatAndCallerInfo() throws IOException {
        loggingService.logInfo("Informational message: %s", "Data processed");

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());

        assertTrue(content.contains("INFO"));
        assertTrue(content.contains("Informational message: Data processed"));
        assertTrue(content.contains("[LoggingServiceTest.shouldLogInfoWithCorrectFormatAndCallerInfo]"));
    }
}
