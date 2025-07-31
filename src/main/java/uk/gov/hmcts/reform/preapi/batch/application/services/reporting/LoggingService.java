package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LoggingService {
    @Getter
    private boolean debugEnabled = false;

    @Setter
    private int totalMigrated = 0;

    @Setter
    private int totalInvited = 0;

    @Setter
    private int totalRecords;

    @Getter
    private int processedRecords = 0;

    @Getter
    private int totalFailed = 0;

    protected LocalDateTime startTime;
    protected final Map<String, Integer> failedCategoryCounts = new HashMap<>();

    private static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/Migration Reports/output.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void initializeLogFile() {
        startTime = LocalDateTime.now();

        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE_PATH, false))) {
            writer.println("=====================================================");
            writer.println(LocalDateTime.now().format(FORMATTER) + " |  Vodafone ETL Job Started");
            writer.println("=====================================================");
        } catch (IOException e) {
            log.error("Failed to initialize output.log: {}", e.getMessage());
        }
    }

    public synchronized void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logMessage = String.format("%s [%s] %s", timestamp, level, message);

        try (FileWriter fileWriter = new FileWriter(LOG_FILE_PATH, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.println(logMessage);
        } catch (IOException e) {
            log.error("Failed to initialize output.log: {}", e.getMessage());
        }
    }

    public void logInfo(String format, Object... args) {
        String message = String.format(format, args);
        log.info(message);
        log("INFO", String.format("%s - %s", getCallerInfo(), message));
    }

    public void logWarning(String format, Object... args) {
        String message = String.format(format, args);
        log.warn(message);
        log("WARN", String.format("%s - %s", getCallerInfo(), message));
    }

    public void logError(String format, Object... args) {
        String message = String.format(format, args);
        log.error(message);
        log("ERROR", String.format("%s - %s", getCallerInfo(), message));
    }

    public void logDebug(String format, Object... args) {
        if (!debugEnabled) {
            return;
        }
        String message = String.format(format, args);
        String callerInfo = getCallerInfo();
        log("DEBUG", String.format("%s - %s", callerInfo, message));
    }

    private String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            if (!className.equals(LoggingService.class.getName())
                && !className.equals(Thread.class.getName())) {
                String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                return String.format("[%s.%s]", simpleClassName, element.getMethodName());
            }
        }
        return "[Unknown]";
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        if (enabled) {
            log("INFO", "Debug logging enabled");
        }
    }

    // ==============================
    // PROGRESS TRACKING
    // ==============================

    public void incrementProgress() {
        processedRecords++;
        refreshProgressBar();
    }

    private synchronized void refreshProgressBar() {
        int progressWidth = 40;
        double percentage = Math.min((processedRecords * 100.0) / totalRecords, 100.0);
        int filledLength = Math.max((int) (progressWidth * (percentage / 100)), 0);

        String progressBar = "[" + "=".repeat(filledLength) + " ".repeat(progressWidth - filledLength) + "]";
        String progressText = String.format("Processing: %d/%d (%.1f%%)", processedRecords, totalRecords, percentage);

        System.out.print("\r\033[K" + progressBar + " " + progressText);
        System.out.flush();
    }

    // ==============================
    // METRICS TRACKING
    // ==============================

    public void setTotalFailed(Map<String, List<FailedItem>> categorizedFailures, List<TestItem> testFailures) {
        int totalTests = testFailures.size();
        this.totalFailed = categorizedFailures.values().stream().mapToInt(List::size).sum() + totalTests;
        failedCategoryCounts.clear();
        categorizedFailures.forEach((category, items) -> failedCategoryCounts.put(category, items.size()));
    }

    public void logSummary() {
        if (startTime == null) {
            logWarning("Start time was not set. Using current time as fallback.");
            startTime = LocalDateTime.now();
        }

        var endTime = LocalDateTime.now();
        Duration duration = Duration.between(startTime, endTime);
        long seconds = duration.getSeconds();

        String summary = String.format(
            """
                =====================================================
                                   BATCH SUMMARY                    \s
                =====================================================
                | %-25s | %10d\s
                | %-25s | %10d\s
                | %-25s | %10d\s
                | %-25s | %10s sec\s
                =====================================================
                """,
            "Total Records Processed", totalRecords,
            "Total Migrated Items", totalMigrated,
            "Total Failed Items", totalFailed,
            "Total Execution Time", seconds
        );

        try (FileWriter fileWriter = new FileWriter(LOG_FILE_PATH, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.println(summary);
        } catch (IOException e) {
            log.error("Failed to write summary to output.log: {}", e.getMessage());
        }
    }
}
