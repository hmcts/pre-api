package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { MigrationTrackerService.class })
public class MigrationTrackerServiceTest {
    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private MigrationTrackerService migrationTrackerService;

    @TempDir
    private Path tempDir;

    private static MockedStatic<ReportCsvWriter> reportCsvWriter;

    @BeforeAll
    static void setUp() {
        reportCsvWriter = Mockito.mockStatic(ReportCsvWriter.class);
    }

    @AfterEach
    void clear() {
        migrationTrackerService.categorizedFailures.clear();
        migrationTrackerService.migratedItems.clear();
        migrationTrackerService.testFailures.clear();
        migrationTrackerService.notifyItems.clear();
        migrationTrackerService.invitedUsers.clear();
    }

    @AfterAll
    public static void tearDown() {
        reportCsvWriter.close();
    }

    @Test
    void addMigratedItem() {
        PassItem item = mock(PassItem.class);

        migrationTrackerService.addMigratedItem(item);
        assertThat(migrationTrackerService.migratedItems).hasSize(1);
    }

    @Test
    void addNotifyItem() {
        NotifyItem item = mock(NotifyItem.class);

        migrationTrackerService.addNotifyItem(item);

        assertThat(migrationTrackerService.notifyItems).hasSize(1);
    }

    @Test
    void addTestItem() {
        TestItem item = mock(TestItem.class);
        CSVArchiveListData csvArchiveListData = mock(CSVArchiveListData.class);
        when(item.getArchiveItem()).thenReturn(csvArchiveListData);
        when(csvArchiveListData.getFileName()).thenReturn("testFile");

        migrationTrackerService.addTestItem(item);

        assertThat(migrationTrackerService.testFailures).hasSize(1);

        verify(loggingService, times(1)).logInfo(anyString(), anyString(), anyString());
    }

    @Test
    void addFailedItem() {
        FailedItem item = mock(FailedItem.class);
        when(item.getFailureCategory()).thenReturn("Category");
        migrationTrackerService.addFailedItem(item);
        assertThat(migrationTrackerService.categorizedFailures.get("Category")).hasSize(1);
        verify(loggingService, times(1)).logInfo(anyString(), anyString(), isNull());
    }

    @Test
    void addInvitedUser() {
        CreateInviteDTO user = mock(CreateInviteDTO.class);
        migrationTrackerService.addInvitedUser(user);
        assertThat(migrationTrackerService.invitedUsers).hasSize(1);
    }

    @Test
    void writeMigratedItemsToCsvSuccess() {
        String fileName = "test.csv";
        String outputDir = tempDir.toString();
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(null);

        migrationTrackerService.addMigratedItem(createMockPassItem());

        migrationTrackerService.writeMigratedItemsToCsv(fileName, outputDir);

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.MIGRATED_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, never()).logError(any(), any());
    }

    @Test
    void writeMigratedItemsToCsvFailure() {
        String fileName = "test.csv";
        String outputDir = tempDir.toString();
        migrationTrackerService.addMigratedItem(createMockPassItem());
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(IOException.class);

        migrationTrackerService.writeMigratedItemsToCsv(fileName, outputDir);

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.MIGRATED_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, times(1)).logError(any(), any());
    }

    @Test
    void writeNotifyItemsToCsvSuccess() {
        String fileName = "test.csv";
        String outputDir = tempDir.toString();
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(null);

        migrationTrackerService.addNotifyItem(createMockNotifyItem());

        migrationTrackerService.writeNotifyItemsToCsv(fileName, outputDir);

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.NOTIFY_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, never()).logError(any(), any());
    }

    @Test
    void writeNotifyItemsToCsvFailure() {
        String fileName = "test.csv";
        String outputDir = tempDir.toString();
        migrationTrackerService.addNotifyItem(createMockNotifyItem());
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(IOException.class);

        migrationTrackerService.writeNotifyItemsToCsv(fileName, outputDir);

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.NOTIFY_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, times(1)).logError(any(), any());
    }

    @Test
    void writeTestFailureReportSuccess() {
        String fileName = "test.csv";
        String outputDir = tempDir.toString();
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(null);

        migrationTrackerService.addTestItem(createMockTestItem());

        migrationTrackerService.writeTestFailureReport(fileName, outputDir);

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.TEST_FAILURE_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, never()).logError(any(), any());
    }

    @Test
    void writeTestFailureReportFailure() {
        String fileName = "test.csv";
        String outputDir = tempDir.toString();
        migrationTrackerService.addTestItem(createMockTestItem());
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(IOException.class);

        migrationTrackerService.writeTestFailureReport(fileName, outputDir);

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.TEST_FAILURE_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, times(1)).logError(any(), any());
    }

    @Test
    void writeCategorizedFailureReportsSuccess() {
        ExtractedMetadata extractedMetadata1 = new ExtractedMetadata();
        extractedMetadata1.setFileName("test.csv");
        extractedMetadata1.setFileSize("10");

        ExtractedMetadata extractedMetadata2 = new ExtractedMetadata();
        extractedMetadata1.setFileName("test2.csv");
        extractedMetadata1.setFileSize("15");

        FailedItem failedItem1 = new FailedItem(extractedMetadata1, "ReasonA", "CategoryA");
        FailedItem failedItem2 = new FailedItem(extractedMetadata2, "ReasonB", "CategoryB");

        migrationTrackerService.addFailedItem(failedItem1);
        migrationTrackerService.addFailedItem(failedItem2);

        String outputDir = tempDir.toString();
        migrationTrackerService.writeCategorizedFailureReports(outputDir);

        verify(loggingService, times(1)).logInfo("Writing failure report for category: %s | %d items", "CategoryA", 1);
        verify(loggingService, times(1)).logInfo("Writing failure report for category: %s | %d items", "CategoryB", 1);
        verify(loggingService, never()).logError(anyString(), any());
    }

    @Test
    void writeCategorizedFailureReportsFailure() {
        ExtractedMetadata extractedMetadata1 = new ExtractedMetadata();
        extractedMetadata1.setFileName("test.csv");
        extractedMetadata1.setFileSize("5");
        FailedItem failedItem = new FailedItem(extractedMetadata1, "ReasonA", "CategoryA");

        migrationTrackerService.addFailedItem(failedItem);
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(IOException.class);

        String outputDir = tempDir.toString();
        migrationTrackerService.writeCategorizedFailureReports(outputDir);

        verify(loggingService, times(1))
            .logError("Failed to write failure report for %s: %s", "CategoryA", null);
    }

    @Test
    void writeInvitedUsersToCsvSuccess() {
        CreateInviteDTO user1 = new CreateInviteDTO();
        user1.setUserId(UUID.randomUUID());
        user1.setFirstName("Example");
        user1.setLastName("One");
        user1.setEmail("example.one@example.com");

        CreateInviteDTO user2 = new CreateInviteDTO();
        user2.setUserId(UUID.randomUUID());
        user2.setFirstName("Example");
        user2.setLastName("Two");
        user2.setEmail("example.two@example.com");

        migrationTrackerService.addInvitedUser(user1);
        migrationTrackerService.addInvitedUser(user2);

        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(null);

        migrationTrackerService.writeNewUserReport();

        verify(loggingService, never()).logError(any(), any());
    }

    @Test
    void writeInvitedUsersToCsvFailure() {
        CreateInviteDTO user1 = new CreateInviteDTO();
        user1.setUserId(UUID.randomUUID());
        user1.setFirstName("Example");
        user1.setLastName("One");
        user1.setEmail("example.one@example.com");
        migrationTrackerService.addInvitedUser(user1);
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(IOException.class);

        migrationTrackerService.writeNewUserReport();

        verify(loggingService, times(1)).logError(any(), any());
    }

    @Test
    void writeAllToCsv() {
        migrationTrackerService.writeAllToCsv();

        verify(loggingService, times(1)).setTotalMigrated(0);
        verify(loggingService, times(1)).setTotalFailed(any(), any());
        verify(loggingService, times(1)).logSummary();
    }

    private PassItem createMockPassItem() {
        ExtractedMetadata metadata = mock(ExtractedMetadata.class);
        ProcessedRecording cleansedData = mock(ProcessedRecording.class);

        when(metadata.getArchiveName()).thenReturn("Test Archive");
        when(metadata.getFileName()).thenReturn("testfile.mp4");
        when(metadata.getFileSize()).thenReturn("100MB");

        when(cleansedData.getCaseReference()).thenReturn("CASE123");
        when(cleansedData.getWitnessFirstName()).thenReturn("John");
        when(cleansedData.getDefendantLastName()).thenReturn("Doe");
        when(cleansedData.getRecordingTimestamp()).thenReturn(Timestamp.from(Instant.now()));
        when(cleansedData.getState()).thenReturn(CaseState.OPEN);
        when(cleansedData.getRecordingVersionNumber()).thenReturn(1);
        when(cleansedData.getDuration()).thenReturn(Duration.ofMinutes(3));

        PassItem passItem = mock(PassItem.class);
        when(passItem.item()).thenReturn(metadata);
        when(passItem.cleansedData()).thenReturn(cleansedData);

        return passItem;
    }

    private NotifyItem createMockNotifyItem() {
        ExtractedMetadata metadata = mock(ExtractedMetadata.class);

        when(metadata.getArchiveName()).thenReturn("Test Archive");
        when(metadata.getCourtReference()).thenReturn("court_one");
        when(metadata.getDefendantLastName()).thenReturn("defendant");
        when(metadata.getWitnessFirstName()).thenReturn("witness");

        NotifyItem notifyItem = mock(NotifyItem.class);
        when(notifyItem.getExtractedMetadata()).thenReturn(metadata);
        when(notifyItem.getNotification()).thenReturn("Notification");

        return notifyItem;
    }

    private TestItem createMockTestItem() {
        CSVArchiveListData archiveItem = mock(CSVArchiveListData.class);

        when(archiveItem.getArchiveName()).thenReturn("Test Archive");
        when(archiveItem.getCreateTime()).thenReturn(Timestamp.from(Instant.now()).toString());
        when(archiveItem.getFileName()).thenReturn("testfile.mp4");
        when(archiveItem.getFileSize()).thenReturn("100");

        TestItem testItem = mock(TestItem.class);
        when(testItem.getArchiveItem()).thenReturn(archiveItem);
        when(testItem.isDurationCheck()).thenReturn(true);
        when(testItem.getDurationInSeconds()).thenReturn(180);
        when(testItem.isKeywordCheck()).thenReturn(true);
        when(testItem.getKeywordFound()).thenReturn("keyword");
        when(testItem.isRegexFailure()).thenReturn(false);

        return testItem;
    }
}
