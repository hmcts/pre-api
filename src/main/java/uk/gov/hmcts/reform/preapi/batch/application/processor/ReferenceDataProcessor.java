package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.RedisService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processor for handling reference data like sites and channel user data.
 */
@Component
public class ReferenceDataProcessor implements ItemProcessor<Object, Object> {
    private final RedisService redisService;
    private LoggingService loggingService;

    public ReferenceDataProcessor(RedisService redisService, LoggingService loggingService) {
        this.redisService = redisService;
        this.loggingService = loggingService;
    }

    @Override
    public Object process(Object item) {
        try {
            if (item instanceof CSVSitesData) {
                processSitesData((CSVSitesData) item);
            } else if (item instanceof CSVChannelData) {
                processChannelUserData((CSVChannelData) item);
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
        redisService.saveHashValue(
            Constants.RedisKeys.SITES_DATA, 
            sitesItem.getSiteReference(), 
            sitesItem.getCourtName()
        );
    }

    public Map<String, String> fetchSitesData() {
        return redisService.getHashAll(Constants.RedisKeys.SITES_DATA, String.class, String.class);
    }


    // ==================================================
    // Channel user reference data
    // ==================================================
    private void processChannelUserData(CSVChannelData channelDataItem) {
        List<String[]> channelList = getExistingChannelList(channelDataItem.getChannelName());
        channelList.add(createChannelUserEntry(channelDataItem));

        redisService.saveHashValue(
            Constants.RedisKeys.CHANNEL_DATA, 
            channelDataItem.getChannelName(), 
            channelList
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<String[]>> fetchChannelUserDataMap() {
        return (Map<String, List<String[]>>)
            (Map<?, ?>) redisService.getHashAll(Constants.RedisKeys.CHANNEL_DATA, String.class, List.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> fetchChannelUserDataKeys() {
        Map<String, List<String[]>> channelDataMap = (Map<String, List<String[]>>)
            (Map<?, ?>) redisService.getHashAll(Constants.RedisKeys.CHANNEL_DATA, String.class, List.class);
        return (channelDataMap != null) ? channelDataMap.keySet() : Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private List<String[]> getExistingChannelList(String channelName) {
        List<String[]> channelList = redisService.getHashValue(
            Constants.RedisKeys.CHANNEL_DATA,
            channelName,
            List.class
        );
        return channelList != null ? channelList : new ArrayList<>();
    }

    private String[] createChannelUserEntry(CSVChannelData channelData) {
        return new String[]{
            channelData.getChannelUser(),
            channelData.getChannelUserEmail()
        };
    }

}
