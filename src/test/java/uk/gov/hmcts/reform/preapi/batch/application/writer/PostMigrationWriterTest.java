package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMigrationWriterTest {

    @Mock
    private LoggingService loggingService;

    @Mock
    private PostMigrationItemExecutor executor;

    private PostMigrationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new PostMigrationWriter(loggingService, executor);
    }

    @Test
    void write_logsAndProcessesEachItem() throws Exception {
        PostMigratedItemGroup first = new PostMigratedItemGroup();
        PostMigratedItemGroup second = new PostMigratedItemGroup();
        when(executor.processOneItem(any())).thenReturn(true);

        writer.write(new Chunk<>(List.of(first, second)));

        verify(loggingService).logInfo("PostMigrationWriter triggered with %d item(s)", 2);
        verify(loggingService, times(2)).logDebug(eq("Processing post-migration item group: %s"), any());
        verify(loggingService, times(2)).logDebug("Successfully processed post-migration item");
        verify(loggingService, never()).logWarning(any());
        verify(loggingService, never()).logError(any(), any());
        verify(executor).processOneItem(first);
        verify(executor).processOneItem(second);
    }

    @Test
    void write_logsWarningWhenExecutorReturnsFalse() throws Exception {
        PostMigratedItemGroup first = new PostMigratedItemGroup();
        PostMigratedItemGroup second = new PostMigratedItemGroup();
        when(executor.processOneItem(any())).thenReturn(false, true);

        writer.write(new Chunk<>(List.of(first, second)));

        verify(loggingService, times(2)).logDebug(eq("Processing post-migration item group: %s"), any());
        verify(loggingService, times(1)).logWarning("Failed to process post-migration item");
        verify(loggingService, times(1)).logDebug("Successfully processed post-migration item");
    }

    @Test
    void write_logsErrorWhenExecutorThrows() throws Exception {
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        doThrow(new RuntimeException("boom")).when(executor).processOneItem(item);

        writer.write(new Chunk<>(List.of(item)));

        verify(loggingService).logDebug(eq("Processing post-migration item group: %s"), eq(item));
        verify(loggingService).logError("Failed to process post-migration item: %s", "boom");
    }

    @Test
    void write_withEmptyChunkLogsZeroItems() throws Exception {
        writer.write(new Chunk<>());

        verify(loggingService).logInfo("PostMigrationWriter triggered with %d item(s)", 0);
        verify(executor, never()).processOneItem(any());
    }
}
