package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.jetbrains.annotations.NotNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class MigrationWriter implements ItemWriter<MigratedItemGroup> {

    private final LoggingService loggingService;
    private final MigrationItemExecutor executor;
    private final MigrationRecordService migrationRecordService;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Autowired
    public MigrationWriter(final LoggingService loggingService,
                           final MigrationRecordService migrationRecordService,
                           final MigrationItemExecutor executor) {
        this.loggingService = loggingService;
        this.migrationRecordService = migrationRecordService;
        this.executor = executor;
    }

    @Override
    public void write(@NotNull Chunk<? extends MigratedItemGroup> items) {
        List<MigratedItemGroup> migratedItems = items.getItems().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        loggingService.logInfo("Processing chunk with %d migrated items", items.size());

        if (migratedItems.isEmpty()) {
            loggingService.logWarning("No valid items found in the current chunk.");
            return;
        }

        for (MigratedItemGroup item : migratedItems) {
            try {
                loggingService.logDebug("Processing case: %s", item.getCase().getReference());
                boolean ok = executor.processOneItem(item); 
                if (ok) {
                    successCount.incrementAndGet();
                    migrationRecordService.updateToSuccess(item.getPassItem().cleansedData().getArchiveId());
                    loggingService.markSuccess();
                } else {
                    failureCount.incrementAndGet();
                    loggingService.markHandled();
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                loggingService.logError("Failed to process migrated item: %s | %s",
                    item.getCase().getReference(), e.getMessage());

            }
        }

        loggingService.logInfo(
            "Batch processing - Successful: %d, Failed: %d",
            successCount.get(), failureCount.get()
        );
    }
}