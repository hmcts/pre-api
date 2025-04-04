package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.FailedItem;
import uk.gov.hmcts.reform.preapi.batch.entities.NotifyItem;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { MigrationTrackerService.class })
public class MigrationTrackerServiceTest {
    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private MigrationTrackerService migrationTrackerService;

    private static MockedStatic<ReportCsvWriter> reportCsvWriter;

    @BeforeAll
    static void setUp() {
        reportCsvWriter = Mockito.mockStatic(ReportCsvWriter.class);
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
        verify(loggingService, times(1)).logInfo(anyString(), anyString(), anyString());
    }

    @Test
    void addInvitedUser() {
        CreateInviteDTO user = mock(CreateInviteDTO.class);
        migrationTrackerService.addInvitedUser(user);
        assertThat(migrationTrackerService.invitedUsers).hasSize(1);
    }

    @Test
    void writeMigratedItemsToCsv() {

    }
}
