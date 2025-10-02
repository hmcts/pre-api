package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;

@Component
public class PostMigrationWriter implements ItemWriter<PostMigratedItemGroup> {

    private final LoggingService loggingService;
    private final PostMigrationItemExecutor executor;

    @Autowired
    public PostMigrationWriter(final LoggingService loggingService,
                               final PostMigrationItemExecutor executor) {
        this.loggingService = loggingService;
        this.executor = executor;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void write(Chunk<? extends PostMigratedItemGroup> items) {
        loggingService.logInfo("PostMigrationWriter triggered with %d item(s)", items.size());

        for (PostMigratedItemGroup item : items) {
            try {
                loggingService.logDebug("Processing post-migration item group: %s", item);
                boolean ok = executor.processOneItem(item);
                if (ok) {
                    loggingService.logDebug("Successfully processed post-migration item");
                } else {
                    loggingService.logWarning("Failed to process post-migration item");
                }
            } catch (Exception e) {
                loggingService.logError("Failed to process post-migration item: %s", e.getMessage());
            }
        }
    }
}
