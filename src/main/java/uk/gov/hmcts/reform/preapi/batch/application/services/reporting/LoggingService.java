package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import org.springframework.stereotype.Service;

import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

@Service
public class LoggingService {
    private static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/Migration Reports/output.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private boolean debugEnabled = false;
    private int totalRecords;  
    private int processedRecords = 0;
    private int totalMigrated = 0;
    private int totalFailed = 0;
    private Map<String, Integer> failedCategoryCounts = new HashMap<>();

    private int unaccountedRecords = 0;
    private int totalInvited = 0;
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public void initializeLogFile() {
        setTotalRecordsFromFile("src/main/resources/batch/Archive_List.csv");
        startTime = LocalDateTime.now();
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE_PATH, false))) {
            writer.println("=====================================================");
            writer.println(LocalDateTime.now().format(FORMATTER) + " |  Vodafone ETL Job Started");
            writer.println("=====================================================");
        } catch (IOException e) {
            System.err.println("Failed to initialize output.log: " + e.getMessage());
        }
    }
    
    public synchronized void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        
        String logMessage = String.format("%s [%s] %s", timestamp, level, message);
        
        try (FileWriter fileWriter = new FileWriter(LOG_FILE_PATH, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.println(logMessage);
        } catch (IOException e) {
            System.err.println("Failed to write to output.log: " + e.getMessage());
        }
    }

    public void logInfo(String format, Object... args) {
        String message = String.format(format, args);
        log("INFO", message);
    }

    public void logWarning(String format, Object... args) { 
        String message = String.format(format, args);
        log("WARN", message); 
    }
    
    public void logError(String format, Object... args) { 
        String message = String.format(format, args);
        log("ERROR", message); 
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
            if (!className.equals(LoggingService.class.getName()) && 
                !className.equals(Thread.class.getName())) {
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
    
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    // ==============================
    // PROGRESS TRACKING
    // ==============================
    public void setTotalRecords(int count) {
        this.totalRecords = Math.max(count, 1);  
    }

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
    public void setTotalMigrated(int count) { this.totalMigrated = count; }
    public void setTotalFailed(Map<String, List<FailedItem>> categorizedFailures) { 
        // this.totalFailed = count; 
        this.totalFailed = categorizedFailures.values().stream().mapToInt(List::size).sum();
        failedCategoryCounts.clear();
        categorizedFailures.forEach((category, items) -> failedCategoryCounts.put(category, items.size()));
    }
    public void checkAllAccounted(int count) { this.unaccountedRecords = this.totalRecords - this.totalMigrated - this.totalFailed; }
    public void setTotalInvited(int count) { this.totalInvited = count; }

    public void logSummary() {
        if (startTime == null) {
            logWarning("Start time was not set. Using current time as fallback.");
            startTime = LocalDateTime.now();
        }
        
        endTime = LocalDateTime.now();
        Duration duration = Duration.between(startTime, endTime);
        long seconds = duration.getSeconds();

        String summary = String.format(
            "\n=====================================================\n" +
            "                   BATCH SUMMARY                     \n" +
            "=====================================================\n" +
            "| %-25s | %10d \n" +
            "| %-25s | %10d \n" +
            "| %-25s | %10d \n" +
            "| %-25s | %10d \n" +
            "| %-25s | %10d \n" +
            "| %-25s | %10s sec \n" + 
            "=====================================================\n",
            "Total Records Processed", totalRecords,
            "Total Migrated Items", totalMigrated,
            "Total Failed Items", totalFailed,
            "Total Unaccounted Items", unaccountedRecords,
            "Total Invited Users", totalInvited,
            "Total Execution Time", String.format("%10d", seconds)
        );


        try (FileWriter fileWriter = new FileWriter(LOG_FILE_PATH, true);
            PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.println(summary);
        } catch (IOException e) {
            System.err.println("Failed to write summary to output.log: " + e.getMessage());
        }

    }


    // ==============================
    // SETTING RECORDS FROM FILE
    // ==============================
    public void setTotalRecordsFromFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
            long lineCount = reader.lines()
                .skip(1) // Skip header
                .filter(line -> !line.trim().isEmpty()) 
                .count();

            this.totalRecords = Math.max((int) lineCount, 1); 
            logInfo("Total records set from file '%s': %d", filePath, this.totalRecords);

        } catch (IOException e) {
            logError("Failed to count lines in file: %s | %s", filePath, e.getMessage());
            this.totalRecords = 1;
        }
    }

}
