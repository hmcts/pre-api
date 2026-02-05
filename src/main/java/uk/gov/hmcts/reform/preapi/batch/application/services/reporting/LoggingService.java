package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
public class LoggingService {
    @Getter
    private boolean debugEnabled;

    @Value("${migration.loggingFile:}")
    private String configuredPath;

    private volatile boolean fileLoggingEnabled = false;
    private Path logPath;

    @Setter
    private int totalMigrated;

    @Setter
    private int totalInvited;

    @Setter
    private int totalRecords;

    @Getter
    private int processedRecords;

    @Getter
    private int totalFailed;

    protected LocalDateTime startTime;
    protected final Map<String, Integer> failedCategoryCounts = new HashMap<>();

    private int handled = 0;
    private static final int LOG_EVERY_N = 10;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void initializeLogFile() {
        startTime = LocalDateTime.now();

        // file logging disabled in cron
        if (configuredPath == null || configuredPath.isBlank()) {
            log.info("Migration file logging disabled (no path configured).");
            return;
        }

        try {
            logPath = Paths.get(configuredPath);
            Files.createDirectories(logPath.getParent());

            try (PrintWriter writer = new PrintWriter(
                        Files.newBufferedWriter(logPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING))) {
                writer.println("=====================================================");
                writer.println(LocalDateTime.now().format(FORMATTER) + " |  Vodafone ETL Job Started");
                writer.println("=====================================================");
            }
            fileLoggingEnabled = true;
            log.info("Migration file logging enabled at {}", logPath);
        } catch (Exception e) {
            fileLoggingEnabled = false;
            log.warn("Migration file logging unavailable ({}). Proceeding without file.", e.getMessage());
        }
    }

    public synchronized void log(String level, String message) {
        if (!fileLoggingEnabled) {
            return;
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logMessage = String.format("%s [%s] %s", timestamp, level, message);

        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            out.println(logMessage);
        } catch (IOException e) {
            fileLoggingEnabled = false;
            log.warn("Disabling migration file logging (write failed: {}).", e.getMessage());
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

    public synchronized void startRun(String label, int total) {
        this.totalRecords = Math.max(total, 0);
        this.processedRecords = 0;
        this.startTime = LocalDateTime.now();
        logInfo("Found %,d %s to process", totalRecords, label == null ? "items" : label);
    }

    public synchronized void markHandled() {
        handled++;
        if (handled % LOG_EVERY_N == 0 || handled == totalRecords) {
            double pct = totalRecords > 0 ? (handled * 100.0) / totalRecords : 0.0;
            logInfo("Handled %,d of %,d (%.1f%%)", handled, totalRecords, pct);
        }
    }

    public synchronized void markSuccess() {
        processedRecords++;
        if (processedRecords % LOG_EVERY_N == 0 || processedRecords == totalRecords) {
            logInfo("PROGRESS - Processed %,d of %,d (%.1f%%)",
                processedRecords, totalRecords, progressPercentage());
        }
    }

    private double progressPercentage() {
        return totalRecords > 0 ? Math.min((processedRecords * 100.0) / totalRecords, 100.0) : 0.0;
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
        if (!fileLoggingEnabled) {
            log.info("Batch summary logging skipped (file logging disabled).");
            return;
        }

        if (startTime == null) {
            logWarning("Start time was not set. Using current time as fallback.");
            startTime = LocalDateTime.now();
        }

        LocalDateTime endTime = LocalDateTime.now();
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

        try (PrintWriter out = new PrintWriter(
            Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            out.println(summary);
        } catch (IOException e) {
            fileLoggingEnabled = false;
            log.warn("Failed to write summary to migration log ({}). Disabling file logging.", e.getMessage());
        }
    }
}
