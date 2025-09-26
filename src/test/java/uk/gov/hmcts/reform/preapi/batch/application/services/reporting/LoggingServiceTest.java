package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoggingServiceTest {
    private LoggingService loggingService;
    private Path logPath;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        loggingService = new LoggingService();
        Path path = tmp.resolve("output.log");
        this.logPath = path; 

        Field f = LoggingService.class.getDeclaredField("configuredPath");
        f.setAccessible(true);
        f.set(loggingService, path.toString());

        loggingService.initializeLogFile();
    }

    private String readLog() throws IOException {
        return Files.readString(logPath);
    }

    @Test
    @DisplayName("Should successfully initialise log file")
    void shouldInitialiseLogFile() throws IOException {
        assertTrue(Files.exists(logPath));
        String content = readLog();
        assertTrue(content.contains("Vodafone ETL Job Started"));
    }

    @Test
    @DisplayName("Should add logs for warnings and errors to file")
    void shouldLogErrorAndWarning() throws IOException {
        loggingService.logError("Something went wrong: %d", 500);
        loggingService.logWarning("Careful: %s", "disk space low");

        String content = readLog();
        assertTrue(content.contains("ERROR"));
        assertTrue(content.contains("WARN"));
    }

    @Test
    @DisplayName("Should not log debug when disabled")
    void shouldNotLogDebugWhenDisabled() throws IOException {
        loggingService.setDebugEnabled(false);
        loggingService.logDebug("Should not be logged");
        String content = readLog();
        assertFalse(content.contains("DEBUG"));
    }

    @Test
    @DisplayName("Should log debug when disabled")
    void shouldLogDebugWhenEnabled() throws IOException {
        loggingService.setDebugEnabled(true);
        loggingService.logDebug("Debug test: %d", 123);
        String content = readLog();
        assertTrue(content.contains("DEBUG"));
    }

    @Test
    @DisplayName("startRun should initialise totals and log header")
    void startRunShouldInitialiseAndLog() throws IOException {
        loggingService.startRun("pending migration records", 23);

        String content = readLog();
        assertTrue(content.contains("Found 23 pending migration records to process")
            || content.contains("Found 23 items to process")); 
    }

    @Test
    @DisplayName("markHandled should log every N and at completion")
    void markHandledShouldLogEveryNAndCompletion() throws IOException {
        loggingService.initializeLogFile();
        loggingService.startRun("items", 20); 

        for (int i = 0; i < 9; i++) {
            loggingService.markHandled();
        }
        String before10 = readLog();
        assertFalse(before10.contains("Handled 10 of 20"));

        loggingService.markHandled();
        String at10 = readLog();
        assertTrue(at10.contains("Handled 10 of 20 (50.0%)"));

        for (int i = 0; i < 10; i++) {
            loggingService.markHandled();
        }
        String at20 = readLog();
        assertTrue(at20.contains("Handled 20 of 20 (100.0%)"));
    }

    @Test
    @DisplayName("markSuccess should log every N and at completion")
    void markSuccessShouldLogEveryNAndCompletion() throws IOException {
        loggingService.initializeLogFile();
        loggingService.startRun("items", 20);

        for (int i = 0; i < 9; i++) {
            loggingService.markSuccess();
        }
        String before10 = readLog();
        assertFalse(before10.contains("PROGRESS - Processed 10 of 20"));

        loggingService.markSuccess();
        String at10 = readLog();
        assertTrue(at10.contains("PROGRESS - Processed 10 of 20 (50.0%)"));

        for (int i = 0; i < 10; i++) {
            loggingService.markSuccess();
        }
        String at20 = readLog();
        assertTrue(at20.contains("PROGRESS - Processed 20 of 20 (100.0%)"));
    }

    @Test
    @DisplayName("progressPercentage should compute correctly and cap at 100")
    void progressPercentageShouldComputeCorrectly() throws Exception {
        loggingService.startRun("items", 8);

        var pr = LoggingService.class.getDeclaredField("processedRecords");
        pr.setAccessible(true);
        pr.set(loggingService, 5);

        var method = LoggingService.class.getDeclaredMethod("progressPercentage");
        method.setAccessible(true);
        double pct = (double) method.invoke(loggingService);
        assertEquals(62.5, pct, 0.0001);

        pr.set(loggingService, 99);
        pct = (double) method.invoke(loggingService);
        assertEquals(100.0, pct, 0.0001);
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

        String content = readLog();

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

        String content = readLog();

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

        String content = readLog();
        assertTrue(content.contains("| Total Execution Time      |          2 sec"));
    }

    @Test
    @DisplayName("Should handle no processed records gracefully")
    void shouldHandleNoProcessedRecords() throws IOException {
        loggingService.setTotalRecords(0);

        loggingService.logSummary();

        String content = readLog();
        assertTrue(content.contains("| Total Records Processed   |          0"));
        assertTrue(content.contains("| Total Migrated Items      |          0"));
        assertTrue(content.contains("| Total Failed Items        |          0"));
    }

    @Test
    @DisplayName("Should successfully log info")
    void shouldLogInfoWithCorrectFormatAndCallerInfo() throws IOException {
        loggingService.logInfo("Informational message: %s", "Data processed");

        String content = readLog();

        assertTrue(content.contains("INFO"));
        assertTrue(content.contains("Informational message: Data processed"));
        assertTrue(content.contains("[LoggingServiceTest.shouldLogInfoWithCorrectFormatAndCallerInfo]"));
    }

    @Test
    @DisplayName("Should disable file logging after write failure when path is a directory")
    void shouldDisableFileLoggingAfterWriteFailure() throws Exception {
        LoggingService service = new LoggingService();

        Field configuredPath = LoggingService.class.getDeclaredField("configuredPath");
        configuredPath.setAccessible(true);
        configuredPath.set(service, "");
        service.initializeLogFile();

        Field logPath = LoggingService.class.getDeclaredField("logPath");
        logPath.setAccessible(true);
        logPath.set(service, tmp); 

        Field fileLoggingEnabled = LoggingService.class.getDeclaredField("fileLoggingEnabled");
        fileLoggingEnabled.setAccessible(true);
        fileLoggingEnabled.set(service, true);

        service.setDebugEnabled(true);
        service.logError("This will fail to write");

        assertFalse((boolean) fileLoggingEnabled.get(service),
            "file logging should be disabled after write failure");
    }
}
