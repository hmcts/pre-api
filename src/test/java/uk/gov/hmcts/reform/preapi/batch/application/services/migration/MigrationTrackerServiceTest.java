package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.entities.ExtractedMetadata;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.IArchiveData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @Autowired
    private MigrationTrackerService migrationTrackerService;

    @TempDir
    private Path tempDir;

    private static MockedStatic<ReportCsvWriter> reportCsvWriter;

    @BeforeAll
    static void setUp() {
        reportCsvWriter = Mockito.mockStatic(ReportCsvWriter.class);
    }

    @BeforeEach
    void setUpReportContainer() throws Exception {
        migrationTrackerService.startNewReportRun();
        Field reportContainerField = MigrationTrackerService.class.getDeclaredField("reportContainer");
        reportContainerField.setAccessible(true);
        reportContainerField.set(migrationTrackerService, "test-container");
        reportCsvWriter.reset();
    }

    @AfterEach
    void clear() {
        migrationTrackerService.startNewReportRun();
        migrationTrackerService.categorizedFailures.clear();
        migrationTrackerService.migratedItems.clear();
        migrationTrackerService.testFailures.clear();
        migrationTrackerService.notifyItems.clear();
        migrationTrackerService.invitedUsers.clear();
        migrationTrackerService.shareBookings.clear();
        migrationTrackerService.shareBookingReportEntries.clear();
        migrationTrackerService.caseClosureEntries.clear();
        migrationTrackerService.shareInviteFailures.clear();
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
        MigrationRecord migrationRecord = mock(MigrationRecord.class);
        when(item.getArchiveItem()).thenReturn(migrationRecord);
        when(migrationRecord.getFileName()).thenReturn("testFile");

        migrationTrackerService.addTestItem(item);

        assertThat(migrationTrackerService.testFailures).hasSize(1);

        verify(loggingService, times(1)).logInfo(anyString(), anyString(), anyString());
    }

    @Test
    void addFailedItem() {
        FailedItem item = mock(FailedItem.class);
        IArchiveData archiveData = mock(IArchiveData.class);
        when(archiveData.getArchiveName()).thenReturn("Test Archive");
        when(item.getItem()).thenReturn(archiveData);
        when(item.getFailureCategory()).thenReturn("Category");
        when(item.getFileName()).thenReturn("testFile.mp4");

        migrationTrackerService.addFailedItem(item);
        
        assertThat(migrationTrackerService.categorizedFailures.get("Category")).hasSize(1);
        verify(loggingService, times(1)).logInfo(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void addInvitedUser() {
        CreateInviteDTO user = mock(CreateInviteDTO.class);
        migrationTrackerService.addInvitedUser(user);
        assertThat(migrationTrackerService.invitedUsers).hasSize(1);
    }

    @Test
    void writeMigratedItemsToCsvSuccess() {
        String fileName = "test";
        String outputDir = tempDir.toString();
        Path mockPath = tempDir.resolve(fileName + ".csv");
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(mockPath);

        migrationTrackerService.addMigratedItem(createMockPassItem());

        File result = migrationTrackerService.writeMigratedItemsToCsv(fileName, outputDir);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(fileName + ".csv");

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.MIGRATED_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, never()).logError(anyString(), anyString());
    }

    @Test
    void writeMigratedItemsToCsvFailure() {
        String fileName = "test";
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
        String fileName = "test";
        String outputDir = tempDir.toString();
        Path mockPath = tempDir.resolve(fileName + ".csv");
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(mockPath);

        migrationTrackerService.addNotifyItem(createMockNotifyItem());

        File result = migrationTrackerService.writeNotifyItemsToCsv(fileName, outputDir);
        assertThat(result).isNotNull();

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.NOTIFY_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, never()).logError(anyString(), anyString());
    }

    @Test
    void writeNotifyItemsToCsvFailure() {
        String fileName = "test";
        String outputDir = tempDir.toString();
        migrationTrackerService.addNotifyItem(createMockNotifyItem());
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(IOException.class);

        File result = migrationTrackerService.writeNotifyItemsToCsv(fileName, outputDir);

        assertThat(result).isNull();

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.NOTIFY_ITEM_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, times(1)).logError(anyString(), isNull());
    }

    @Test
    void writeTestFailureReportSuccess() {
        String fileName = "test";
        String outputDir = tempDir.toString();
        Path mockPath = tempDir.resolve(fileName + ".csv");
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(mockPath);

        migrationTrackerService.addTestItem(createMockTestItem());

        File result = migrationTrackerService.writeTestFailureReport(fileName, outputDir);
        assertThat(result).isNotNull();

        reportCsvWriter.verify(() -> ReportCsvWriter.writeToCsv(
            eq(MigrationTrackerService.TEST_FAILURE_HEADERS),
            anyList(),
            eq(fileName),
            eq(outputDir),
            eq(false)), times(1));
        verify(loggingService, never()).logError(anyString(), anyString());
    }

    @Test
    void writeTestFailureReportFailure() {
        String fileName = "test";
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
        verify(loggingService, times(1)).logError(anyString(), isNull());
    }

    @Test
    void writeCategorizedFailureReportsSuccess() {
        Path mockPathA = tempDir.resolve("CategoryA.csv");
        Path mockPathB = tempDir.resolve("CategoryB.csv");

        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("CategoryA"), any(), anyBoolean()))
            .thenReturn(mockPathA);
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("CategoryB"), any(), anyBoolean()))
            .thenReturn(mockPathB);

        ExtractedMetadata extractedMetadata1 = new ExtractedMetadata();
        extractedMetadata1.setFileName("test.csv");
        extractedMetadata1.setFileSize("10");

        ExtractedMetadata extractedMetadata2 = new ExtractedMetadata();
        extractedMetadata2.setFileName("test2.csv");
        extractedMetadata2.setFileSize("15");

        FailedItem failedItem1 = new FailedItem(extractedMetadata1, "ReasonA", "CategoryA");
        FailedItem failedItem2 = new FailedItem(extractedMetadata2, "ReasonB", "CategoryB");

        migrationTrackerService.addFailedItem(failedItem1);
        migrationTrackerService.addFailedItem(failedItem2);

        String outputDir = tempDir.toString();
        List<File> result = migrationTrackerService.writeCategorizedFailureReports(outputDir);

        assertThat(result).hasSize(2);

        verify(loggingService, times(1)).logInfo("Writing failure report for category: %s | %d items", "CategoryA", 1);
        verify(loggingService, times(1)).logInfo("Writing failure report for category: %s | %d items", "CategoryB", 1);
        verify(loggingService, never()).logError(anyString(), any());
    }

    @Test
    void writeCategorizedFailureReportsFailure() {
        ExtractedMetadata extractedMetadata1 = new ExtractedMetadata();
        extractedMetadata1.setFileSize("test.csv");
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

        Path mockPath = tempDir.resolve("Invited_users.csv");
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(mockPath);

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
        Path mockMigratedPath = tempDir.resolve("Migrated.csv");
        Path mockSummaryPath  = tempDir.resolve("Summary.csv");
        Path mockTestPath     = tempDir.resolve("Test.csv");
        Path mockNotifyPath   = tempDir.resolve("Notify.csv");

        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), eq("Migrated"), any(), anyBoolean())
        ).thenReturn(mockMigratedPath);

        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), eq("Summary"), any(), anyBoolean())
        ).thenReturn(mockSummaryPath);

        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), eq("Test"), any(), anyBoolean())
        ).thenReturn(mockTestPath);

        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), eq("Notify"), any(), anyBoolean())
        ).thenReturn(mockNotifyPath);

        migrationTrackerService.writeAllToCsv();

        verify(loggingService, times(1)).setTotalMigrated(0);
        verify(loggingService, times(1)).setTotalFailed(any(), any());
        verify(loggingService, times(1)).logSummary();
    }

    @Test
    void writeSummarySuccess() {
        ExtractedMetadata metadata1 = new ExtractedMetadata();
        metadata1.setFileName("test1.csv");
        metadata1.setFileSize("10");
        FailedItem failedItem1 = new FailedItem(metadata1, "ReasonA", "CategoryA");
        
        ExtractedMetadata metadata2 = new ExtractedMetadata();
        metadata2.setFileName("test2.csv");
        metadata2.setFileSize("15");
        FailedItem failedItem2 = new FailedItem(metadata2, "ReasonB", "CategoryA");
        
        migrationTrackerService.addFailedItem(failedItem1);
        migrationTrackerService.addFailedItem(failedItem2);
        migrationTrackerService.addTestItem(createMockTestItem());

        String outputDir = tempDir.toString();
        Path mockPath = tempDir.resolve("Summary.csv");
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("Summary"), any(), anyBoolean()))
            .thenReturn(mockPath);

        try {
            Method writeSummaryMethod = MigrationTrackerService.class.getDeclaredMethod(
                "writeSummary", String.class);
            writeSummaryMethod.setAccessible(true);
            File result = (File) writeSummaryMethod.invoke(migrationTrackerService, outputDir);
            
            assertThat(result).isNotNull();
            verify(loggingService, never()).logError(anyString(), anyString());
        } catch (Exception e) {
            fail("Failed to invoke writeSummary method: " + e.getMessage());
        }
    }


    @Test
    void addShareBookingReportWithNullValues() {
        CreateShareBookingDTO shareBooking = new CreateShareBookingDTO();
        shareBooking.setId(null);
        shareBooking.setBookingId(null);
        shareBooking.setSharedWithUser(null);
        shareBooking.setSharedByUser(null);

        migrationTrackerService.addShareBookingReport(shareBooking, 
            "test@example.com", "robot@example.com");

        assertThat(migrationTrackerService.shareBookingReportEntries).hasSize(1);
        MigrationTrackerService.ShareBookingReportEntry entry = 
            migrationTrackerService.shareBookingReportEntries.get(0);
        assertThat(entry.shareId()).isEmpty();
        assertThat(entry.bookingId()).isEmpty();
        assertThat(entry.sharedWithUserId()).isEmpty();
        assertThat(entry.sharedByUserId()).isEmpty();
    }

    @Test
    void addShareBookingReportWithValidValues() {
        CreateShareBookingDTO shareBooking = new CreateShareBookingDTO();
        shareBooking.setId(UUID.randomUUID());
        shareBooking.setBookingId(UUID.randomUUID());
        shareBooking.setSharedWithUser(UUID.randomUUID());
        shareBooking.setSharedByUser(UUID.randomUUID());

        migrationTrackerService.addShareBookingReport(shareBooking, 
            "test@example.com", "robot@example.com");

        assertThat(migrationTrackerService.shareBookingReportEntries).hasSize(1);
        MigrationTrackerService.ShareBookingReportEntry entry = 
            migrationTrackerService.shareBookingReportEntries.get(0);
        assertThat(entry.shareId()).isEqualTo(shareBooking.getId().toString());
        assertThat(entry.bookingId()).isEqualTo(shareBooking.getBookingId().toString());
        assertThat(entry.sharedWithUserId()).isEqualTo(shareBooking.getSharedWithUser().toString());
        assertThat(entry.sharedByUserId()).isEqualTo(shareBooking.getSharedByUser().toString());
    }



    @Test
    void writeSummaryFailure() {
        String outputDir = tempDir.toString();
        
        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setFileName("test.csv");
        metadata.setFileSize("10");
        FailedItem failedItem = new FailedItem(metadata, "ReasonA", "CategoryA");
        migrationTrackerService.addFailedItem(failedItem);
        
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("Summary"), any(), anyBoolean()))
            .thenThrow(new IOException("Test exception"));

        try {
            Method writeSummary = MigrationTrackerService.class.getDeclaredMethod(
                "writeSummary", String.class);
            writeSummary.setAccessible(true);
            File result = (File) writeSummary.invoke(migrationTrackerService, outputDir);
            
            assertThat(result).isNull();
            verify(loggingService, times(1)).logError(anyString(), anyString());
        } catch (Exception e) {
            fail("Failed to invoke writeSummary method: " + e.getMessage());
        }
    }

    @Test  
    void writeNewUserReportWithNoAzureUpload() {
        CreateInviteDTO user = new CreateInviteDTO();
        user.setUserId(UUID.randomUUID());
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test@example.com");
        
        migrationTrackerService.addInvitedUser(user);
        
        Path mockPath = tempDir.resolve("Invited_users.csv");
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(mockPath);

        migrationTrackerService.writeNewUserReport();

        verify(azureVodafoneStorageService, never()).uploadCsvFile(anyString(), anyString(), any(File.class));
    }

    @Test
    void writeAllToCsvWithAzureUploads() {
        migrationTrackerService.addMigratedItem(createMockPassItem());
        migrationTrackerService.addFailedItem(createMockFailedItem());
        migrationTrackerService.addTestItem(createMockTestItem());
        migrationTrackerService.addNotifyItem(createMockNotifyItem());
        
        Path mockMigratedPath = tempDir.resolve("Migrated.csv");
        Path mockFailurePath = tempDir.resolve("Summary.csv");
        Path mockTestPath = tempDir.resolve("Test.csv");
        Path mockNotifyPath = tempDir.resolve("Notify.csv");
        
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("Migrated"), any(), anyBoolean()))
            .thenReturn(mockMigratedPath);
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("Summary"), any(), anyBoolean()))
            .thenReturn(mockFailurePath);
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("Test"), any(), anyBoolean()))
            .thenReturn(mockTestPath);
        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), eq("Notify"), any(), anyBoolean()))
            .thenReturn(mockNotifyPath);
        
        try {
            Files.createFile(mockMigratedPath);
            Files.createFile(mockFailurePath);
            Files.createFile(mockTestPath);
            Files.createFile(mockNotifyPath);
        } catch (IOException e) {
            fail("Failed to create mock files");
        }
        
        migrationTrackerService.writeAllToCsv();
        
        verify(azureVodafoneStorageService, times(4)).uploadCsvFile(anyString(), anyString(), any(File.class));
        verify(loggingService, times(1)).setTotalRecords(anyInt());
    }

    @Test
    void buildFailedItemsRowsWithUnknownType() {
        FailedItem item = new FailedItem(mock(IArchiveData.class), "Unknown reason", "UnknownCategory");
        migrationTrackerService.addFailedItem(item);

        List<FailedItem> items = List.of(item);
        List<List<String>> rows = migrationTrackerService.buildFailedItemsRows(items);

        assertThat(rows).isEmpty(); 
        verify(loggingService, times(1)).logWarning(eq("Skipping unknown item type: %s"), anyString());
    }

    @Test
    void buildInvitedUserRowsWithData() {
        CreateInviteDTO user1 = new CreateInviteDTO();
        user1.setUserId(UUID.randomUUID());
        user1.setFirstName("John");
        user1.setLastName("Doe");
        user1.setEmail("john.doe@example.com");

        CreateInviteDTO user2 = new CreateInviteDTO();
        user2.setUserId(UUID.randomUUID());
        user2.setFirstName("Jane");
        user2.setLastName("Smith");
        user2.setEmail("jane.smith@example.com");

        migrationTrackerService.addInvitedUser(user1);
        migrationTrackerService.addInvitedUser(user2);

        try {
            Method buildInvitedUserRowsMethod = MigrationTrackerService.class.getDeclaredMethod("buildInvitedUserRows");
            buildInvitedUserRowsMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<List<String>> result = (List<List<String>>) buildInvitedUserRowsMethod.invoke(migrationTrackerService);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).hasSize(4);
            assertThat(result.get(0).get(1)).isEqualTo("John");
            assertThat(result.get(0).get(2)).isEqualTo("Doe");
            assertThat(result.get(0).get(3)).isEqualTo("john.doe@example.com");
        } catch (Exception e) {
            fail("Failed to invoke buildInvitedUserRows method: " + e.getMessage());
        }
    }

    @Test
    void addMultipleFailedItemsToSameCategory() {
        ExtractedMetadata metadata1 = new ExtractedMetadata();
        metadata1.setFileName("file1.mp4");
        metadata1.setFileSize("100");
        FailedItem failedItem1 = new FailedItem(metadata1, "Reason 1", "SameCategory");
        
        ExtractedMetadata metadata2 = new ExtractedMetadata();
        metadata2.setFileName("file2.mp4");
        metadata2.setFileSize("200");
        FailedItem failedItem2 = new FailedItem(metadata2, "Reason 2", "SameCategory");

        migrationTrackerService.addFailedItem(failedItem1);
        migrationTrackerService.addFailedItem(failedItem2);

        assertThat(migrationTrackerService.categorizedFailures.get("SameCategory")).hasSize(2);
    }

    @Test
    void startNewReportRunResetsOutputDirectory() {
        List<String> outputDirs = new ArrayList<>();

        reportCsvWriter.when(() -> ReportCsvWriter.writeToCsv(any(), any(), any(), any(), anyBoolean()))
            .thenAnswer(invocation -> {
                outputDirs.add(invocation.getArgument(3));
                return tempDir.resolve(invocation.getArgument(2) + ".csv");
            });

        migrationTrackerService.startNewReportRun();
        migrationTrackerService.writeNewUserReport();
        migrationTrackerService.writeShareBookingsReport();

        assertThat(outputDirs).hasSize(2);
        assertThat(outputDirs.get(0)).isEqualTo(outputDirs.get(1));

        String firstTimestamp = getCurrentRunTimestamp();
        assertThat(firstTimestamp).isNotBlank();
        
        migrationTrackerService.startNewReportRun();
        assertThat(getCurrentRunTimestamp()).isNull();
        migrationTrackerService.writeNewUserReport();
        
        assertThat(outputDirs).hasSize(3);
        String secondTimestamp = getCurrentRunTimestamp();
        assertThat(secondTimestamp).isNotBlank();
        assertThat(outputDirs.get(2)).contains(secondTimestamp);
        
        String firstDir = outputDirs.get(0);
        if (!secondTimestamp.equals(firstTimestamp)) {
            assertThat(outputDirs.get(2)).isNotEqualTo(firstDir);
        }
    }

    private String getCurrentRunTimestamp() {
        try {
            Field field = MigrationTrackerService.class.getDeclaredField("currentRunTimestamp");
            field.setAccessible(true);
            return (String) field.get(migrationTrackerService);
        } catch (ReflectiveOperationException e) {
            fail("Failed to read currentRunTimestamp field: " + e.getMessage());
            return null;
        }
    }


    private PassItem createMockPassItem() {
        ExtractedMetadata metadata = mock(ExtractedMetadata.class);
        ProcessedRecording cleansedData = mock(ProcessedRecording.class);

        when(metadata.getArchiveName()).thenReturn("Test Archive");
        when(metadata.getFileName()).thenReturn("testfile.mp4");
        when(metadata.getFileSize()).thenReturn("100");

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

    private CreateShareBookingDTO createShareBooking(UUID shareId) {
        CreateShareBookingDTO dto = new CreateShareBookingDTO();
        dto.setId(shareId);
        dto.setBookingId(UUID.randomUUID());
        dto.setSharedWithUser(UUID.randomUUID());
        dto.setSharedByUser(UUID.randomUUID());
        return dto;
    }

    private FailedItem createMockFailedItem() {
        ExtractedMetadata metadata = new ExtractedMetadata();
        metadata.setFileName("test.mp4");
        metadata.setFileSize("100");
        metadata.setArchiveName("Test Archive");
        return new FailedItem(metadata, "Test reason", "TestCategory");
    }

    private NotifyItem createMockNotifyItem() {
        ProcessedRecording metadata = mock(ProcessedRecording.class);

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
        MigrationRecord archiveItem = mock(MigrationRecord.class);

        when(archiveItem.getArchiveName()).thenReturn("Test Archive");
        when(archiveItem.getCreateTime()).thenReturn(Timestamp.from(Instant.now()));
        when(archiveItem.getFileName()).thenReturn("testfile.mp4");
        when(archiveItem.getFileSizeMb()).thenReturn("100");

        TestItem testItem = mock(TestItem.class);
        when(testItem.getArchiveItem()).thenReturn(archiveItem);
        when(testItem.isDurationCheck()).thenReturn(true);
        when(testItem.getDurationInSeconds()).thenReturn(180);
        when(testItem.isKeywordCheck()).thenReturn(true);
        when(testItem.getKeywordFound()).thenReturn("keyword");
        when(testItem.isRegexFailure()).thenReturn(false);

        return testItem;
    }

    @Test
    void addShareBooking_addsOne() {
        CreateShareBookingDTO row = new CreateShareBookingDTO();
        row.setBookingId(UUID.randomUUID());
        row.setSharedWithUser(UUID.randomUUID());

        migrationTrackerService.addShareBooking(row);

        assertThat(migrationTrackerService.shareBookings).hasSize(1);
    }

    @Test
    void buildShareBookingsRows_withData() {
        UUID shareId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CreateShareBookingDTO row = new CreateShareBookingDTO();
        row.setId(shareId);
        row.setBookingId(bookingId);
        row.setSharedWithUser(userId);

        migrationTrackerService.addShareBooking(row);

        List<List<String>> rows = migrationTrackerService.buildShareBookingsRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(7);
        assertThat(rows.get(0).get(0)).isEqualTo(shareId.toString());
        assertThat(rows.get(0).get(1)).isEqualTo(bookingId.toString());
        assertThat(rows.get(0).get(2)).isEqualTo(userId.toString());
        assertThat(rows.get(0).get(3)).isEmpty();
        assertThat(rows.get(0).get(4)).isEmpty();
        assertThat(rows.get(0).get(5)).isEmpty();
        assertThat(rows.get(0).get(6)).isNotBlank();
    }

    @Test
    void buildShareBookingsRows_usesReportEntriesWhenPresent() {
        UUID shareId = UUID.randomUUID();
        migrationTrackerService.addShareBookingReport(
            createShareBooking(shareId),
            "share@example.com",
            "by@example.com"
        );

        List<List<String>> rows = migrationTrackerService.buildShareBookingsRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(7);
        assertThat(rows.get(0).get(0)).isEqualTo(shareId.toString());
        assertThat(rows.get(0).get(3)).isEqualTo("share@example.com");
        assertThat(rows.get(0).get(4)).isNotBlank();
        assertThat(rows.get(0).get(5)).isEqualTo("by@example.com");
    }

    @Test
    void writeShareBookingsToCsv_success() {
        CreateShareBookingDTO row = new CreateShareBookingDTO();
        row.setBookingId(UUID.randomUUID());
        row.setSharedWithUser(UUID.randomUUID());
        migrationTrackerService.addShareBooking(row);

        Path mockPath = tempDir.resolve("Share_bookings.csv");
        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), anyString(), anyString(), anyBoolean())
        ).thenReturn(mockPath);

        migrationTrackerService.writeShareBookingsToCsv("Share_bookings", tempDir.toString());

        reportCsvWriter.verify(() ->
            ReportCsvWriter.writeToCsv(
                eq(MigrationTrackerService.SHARE_BOOKINGS_HEADERS),
                anyList(),
                eq("Share_bookings"),
                anyString(),
                eq(false)
            ), times(1)
        );
        verify(loggingService, never()).logError(anyString(), anyString());
    }

    @Test
    void writeCaseClosureReport_returnsFileWhenEntriesExist() {
        migrationTrackerService.addCaseClosureEntry(
            new MigrationTrackerService.CaseClosureReportEntry(
                "case-id",
                "REF123",
                "CLOSED",
                ""
            )
        );

        Path mockPath = tempDir.resolve("Case_closure.csv");
        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(
                eq(MigrationTrackerService.CASE_CLOSURE_HEADERS),
                anyList(),
                eq("Case_closure"),
                anyString(),
                eq(false)
            )
        ).thenReturn(mockPath);

        File result = migrationTrackerService.writeCaseClosureReport("Case_closure", tempDir.toString());

        assertThat(result).isNotNull();
        reportCsvWriter.verify(() ->
            ReportCsvWriter.writeToCsv(
                eq(MigrationTrackerService.CASE_CLOSURE_HEADERS),
                anyList(),
                eq("Case_closure"),
                anyString(),
                eq(false)
            ), times(1)
        );
    }

    @Test
    void writeCaseClosureReport_returnsNullWhenNoEntries() {
        File result = migrationTrackerService.writeCaseClosureReport("Case_closure", tempDir.toString());
        assertThat(result).isNull();
        reportCsvWriter.verifyNoInteractions();
    }

    @Test
    void writeShareInviteFailureReport_returnsFileWhenEntriesExist() {
        migrationTrackerService.addShareInviteFailure(
            new MigrationTrackerService.ShareInviteFailureEntry(
                "Invite",
                "identifier",
                "user@example.com",
                "UPSERT",
                "reason",
                "timestamp"
            )
        );

        Path mockPath = tempDir.resolve("Share_invite_failures.csv");
        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(
                eq(MigrationTrackerService.SHARE_INVITE_FAILURE_HEADERS),
                anyList(),
                eq("Share_invite_failures"),
                anyString(),
                eq(false)
            )
        ).thenReturn(mockPath);

        File result = migrationTrackerService.writeShareInviteFailureReport(
            "Share_invite_failures", tempDir.toString());

        assertThat(result).isNotNull();
        reportCsvWriter.verify(() ->
            ReportCsvWriter.writeToCsv(
                eq(MigrationTrackerService.SHARE_INVITE_FAILURE_HEADERS),
                anyList(),
                eq("Share_invite_failures"),
                anyString(),
                eq(false)
            ), times(1)
        );
    }

    @Test
    void writeShareInviteFailureReport_returnsNullWhenNoEntries() {
        File result = migrationTrackerService.writeShareInviteFailureReport(
            "Share_invite_failures", tempDir.toString());
        assertThat(result).isNull();
        reportCsvWriter.verifyNoInteractions();
    }

    @Test
    void writeShareBookingsToCsv_failure_logsError() {
        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), anyString(), anyString(), anyBoolean())
        ).thenThrow(new IOException("boom"));

        migrationTrackerService.writeShareBookingsToCsv("Share_bookings", tempDir.toString());

        verify(loggingService, times(1))
            .logError(eq("Failed to write share bookings CSV: %s"), anyString());
    }

    @Test
    void writeShareBookingsReport_writesCsv() {
        CreateShareBookingDTO row = new CreateShareBookingDTO();
        row.setBookingId(UUID.randomUUID());
        row.setSharedWithUser(UUID.randomUUID());
        migrationTrackerService.addShareBooking(row);

        Path mockPath = tempDir.resolve("Share_bookings.csv");
        reportCsvWriter.when(() ->
            ReportCsvWriter.writeToCsv(any(), any(), eq("Share_bookings"), anyString(), anyBoolean())
        ).thenReturn(mockPath);

        migrationTrackerService.writeShareBookingsReport();

        reportCsvWriter.verify(() ->
            ReportCsvWriter.writeToCsv(
                eq(MigrationTrackerService.SHARE_BOOKINGS_HEADERS),
                anyList(),
                eq("Share_bookings"),
                anyString(), 
                eq(false)
            ), times(1)
        );
    }

    @Test
    void buildFailedItemsRows_withMigrationRecord() {
        MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setArchiveName("Arc");
        migrationRecord.setCreateTime(Timestamp.from(Instant.now()));
        migrationRecord.setFileName("file.mp4");
        migrationRecord.setFileSizeMb("25");

        FailedItem failed = new FailedItem(migrationRecord, "ReasonX", "CatX");

        List<List<String>> rows = migrationTrackerService.buildFailedItemsRows(List.of(failed));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(6);
        assertThat(rows.get(0).get(0)).isEqualTo("ReasonX"); 
        assertThat(rows.get(0).get(1)).isEqualTo("Arc");     
        assertThat(rows.get(0).get(3)).isEqualTo("file.mp4"); 
        assertThat(rows.get(0).get(4)).isEqualTo("25");      
    }

    @Test
    void formatDuration_nullReturnsEmpty() throws Exception {
        Method m = MigrationTrackerService.class.getDeclaredMethod("formatDuration", Duration.class);
        m.setAccessible(true);
        String s = (String) m.invoke(migrationTrackerService, new Object[]{ null });
        assertThat(s).isEqualTo("");
    }

    @Test
    void formatDuration_formatsCorrectly() throws Exception {
        Method m = MigrationTrackerService.class.getDeclaredMethod("formatDuration", Duration.class);
        m.setAccessible(true);
        String s = (String) m.invoke(migrationTrackerService, Duration.ofHours(1).plusMinutes(2).plusSeconds(3));
        assertThat(s).isEqualTo("01:02:03");
    }

    @Test
    void getValueOrEmpty_handlesNullAndNonNull() throws Exception {
        Method m = MigrationTrackerService.class.getDeclaredMethod("getValueOrEmpty", Object.class);
        m.setAccessible(true);

        String empty = (String) m.invoke(migrationTrackerService, new Object[]{ null });
        assertThat(empty).isEqualTo("");

        UUID id = UUID.randomUUID();
        String str = (String) m.invoke(migrationTrackerService, id);
        assertThat(str).isEqualTo(id.toString());
    }

        
}
