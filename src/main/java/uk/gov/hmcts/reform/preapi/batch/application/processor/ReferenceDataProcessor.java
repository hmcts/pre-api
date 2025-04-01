package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processor for handling reference data like sites and channel user data.
 */
@Component
public class ReferenceDataProcessor implements ItemProcessor<Object, Object> {
    private final InMemoryCacheService cacheService;
    private LoggingService loggingService;

    public ReferenceDataProcessor(
        InMemoryCacheService cacheService,
        LoggingService loggingService
    ) {
        this.cacheService = cacheService;
        this.loggingService = loggingService;
    }

    @Override
    public Object process(Object item) {
        try {
            if (item instanceof CSVSitesData csvSitesData) {
                processSitesData(csvSitesData);
            } else if (item instanceof CSVChannelData csvChannelData) {
                processChannelUserData(csvChannelData);
            } else {
                loggingService.logError("Unsupported reference data type: %s", item.getClass().getName());
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
        cacheService.saveHashValue(
            Constants.CacheKeys.SITES_DATA,
            sitesItem.getSiteReference(),
            sitesItem.getCourtName()
        );
    }

    public Map<String, Object> fetchSitesData() {
        return cacheService.getHashAll(Constants.CacheKeys.SITES_DATA);
    }


    // ==================================================
    // Channel user reference data
    // ==================================================
    private void processChannelUserData(CSVChannelData channelDataItem) {
        List<String[]> channelList = cacheService.getAsStringArrayList(
            Constants.CacheKeys.CHANNEL_DATA,
            channelDataItem.getChannelName()
        );

        channelList.add(createChannelUserEntry(channelDataItem));

        cacheService.saveHashValue(
            Constants.CacheKeys.CHANNEL_DATA,
            channelDataItem.getChannelName(),
            channelList
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<String[]>> fetchChannelUserDataMap() {
        return cacheService.getAllAsType(
            Constants.CacheKeys.CHANNEL_DATA,
            Map.class
        );
    }

    public Set<String> fetchChannelUserDataKeys() {
        Map<String, List<String[]>> channelDataMap = fetchChannelUserDataMap();
        return channelDataMap != null ? channelDataMap.keySet() : Collections.emptySet();
    }

    private String[] createChannelUserEntry(CSVChannelData channelData) {
        return new String[]{
            channelData.getChannelUser(),
            channelData.getChannelUserEmail()
        };
    }

}
