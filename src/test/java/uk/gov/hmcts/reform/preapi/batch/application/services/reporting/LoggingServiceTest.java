package uk.gov.hmcts.reform.preapi.batch.application.services.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingServiceTest {

    private LoggingService loggingService;
    private static final String LOG_FILE_PATH = System.getProperty("user.dir") + "/Migration Reports/output.log";

    @BeforeEach
    void setUp() {
        loggingService = new LoggingService();
        new File(LOG_FILE_PATH).delete();
        loggingService.initializeLogFile();
    }

    @Test
    void shouldInitializeLogFile() throws IOException {
        File logFile = new File(LOG_FILE_PATH);
        assertTrue(logFile.exists());
        String content = Files.readString(logFile.toPath());
        assertTrue(content.contains("Vodafone ETL Job Started"));
    }

    @Test
    void shouldLogErrorAndWarning() throws IOException {
        loggingService.logError("Something went wrong: %d", 500);
        loggingService.logWarning("Careful: %s", "disk space low");

        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertTrue(content.contains("ERROR"));
        assertTrue(content.contains("WARN"));
    }

    @Test
    void shouldNotLogDebugWhenDisabled() throws IOException {
        loggingService.setDebugEnabled(false);
        loggingService.logDebug("Should not be logged");
        String content = Files.readString(new File(LOG_FILE_PATH).toPath());
        assertFalse(content.contains("DEBUG"));
    }

    @Test
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
}