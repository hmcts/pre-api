package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Processor for handling reference data like sites and channel user data.
 */
@Component
public class ReferenceDataProcessor implements ItemProcessor<Object, Object> {
    private final InMemoryCacheService cacheService;
    private final LoggingService loggingService;

    @Autowired
    public ReferenceDataProcessor(final InMemoryCacheService cacheService, final LoggingService loggingService) {
        this.cacheService = cacheService;
        this.loggingService = loggingService;
    }

    @Override
    public Object process(Object item) {
        try {
            switch (item) {
                case CSVSitesData csvSitesData -> processSitesData(csvSitesData);
                case CSVChannelData csvChannelData -> processChannelUserData(csvChannelData);
                default -> loggingService.logError(
                    "Unsupported reference data type: %s",
                    item.getClass().getName()
                );
            }
        } catch (Exception e) {
            loggingService.logError("Error processing reference data: %s - %s", e.getMessage(), e);
        }
        return null;
    }

    // =========================================
    // Site reference data
    // =========================================
    private void processSitesData(CSVSitesData sitesItem) {
        cacheService.saveSiteReference(sitesItem.getSiteReference(), sitesItem.getCourtName());
    }

    // ==================================================
    // Channel user reference data
    // ==================================================
    private void processChannelUserData(CSVChannelData channelDataItem) {
        String channelName = channelDataItem.getChannelName();
        List<String[]> existing = cacheService.getChannelReference(channelName)
            .orElse(new ArrayList<>());

        existing.add(createChannelUserEntry(channelDataItem));

        cacheService.saveChannelReference(channelName, existing);
    }

    private String[] createChannelUserEntry(CSVChannelData channelData) {
        return new String[]{
            channelData.getChannelUser(),
            channelData.getChannelUserEmail()
        };
    }
}
