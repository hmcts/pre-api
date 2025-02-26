package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.services.RedisService;

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
    private static final Logger logger = LoggerFactory.getLogger(ReferenceDataProcessor.class);
    private static final String SITES = "sites_data";
    private static final String CHANNEL = "channel_data";

    private final RedisService redisService;

    public ReferenceDataProcessor(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public Object process(Object item) {
        try {
            if (item instanceof CSVSitesData) {
                processSitesData((CSVSitesData) item);
            } else if (item instanceof CSVChannelData) {
                processChannelUserData((CSVChannelData) item);
            } else {
                logger.error("Unsupported reference data type: {}", item.getClass().getName());
            }
        } catch (Exception e) {
            logger.error("Error processing reference data: {}", e.getMessage(), e);
        }
        return null;
    }
   
    // =========================================
    // Site reference data 
    // =========================================
    private void processSitesData(CSVSitesData sitesItem) {
        redisService.saveHashValue(
            SITES, 
            sitesItem.getSiteReference(), 
            sitesItem.getCourtName()
        );
    }

    public Map<String, String> fetchSitesData() {
        return redisService.getHashAll(SITES, String.class, String.class);
    }


    // ==================================================
    // Channel user reference data
    // ==================================================
    private void processChannelUserData(CSVChannelData channelDataItem) {
        List<String[]> channelList = getExistingChannelList(channelDataItem.getChannelName());
        channelList.add(createChannelUserEntry(channelDataItem));

        redisService.saveHashValue(
            CHANNEL, 
            channelDataItem.getChannelName(), 
            channelList
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<String[]>> fetchChannelUserDataMap() {
        return (Map<String, List<String[]>>)
            (Map<?, ?>) redisService.getHashAll(CHANNEL, String.class, List.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> fetchChannelUserDataKeys() {
        Map<String, List<String[]>> channelDataMap = (Map<String, List<String[]>>)
            (Map<?, ?>) redisService.getHashAll(CHANNEL, String.class, List.class);
        return (channelDataMap != null) ? channelDataMap.keySet() : Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private List<String[]> getExistingChannelList(String channelName) {
        List<String[]> channelList = redisService.getHashValue(
            CHANNEL,
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
