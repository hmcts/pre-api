package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Processor for handling reference data like sites and channel user data.
 */
@Component
public class ReferenceDataProcessor implements ItemProcessor<Object, Object> {

    private static final String REDIS_SITES_KEY = "sites_data";
    private final RedisService redisService;
    private final Map<String, List<String[]>> channelUserDataMap = new HashMap<>();

    public ReferenceDataProcessor(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public Object process(Object item) {
        if (item instanceof CSVSitesData) {
            processSitesData((CSVSitesData) item);
        } else if (item instanceof CSVChannelData) {
            processChannelUserData((CSVChannelData) item);
        } else {
            Logger.getAnonymousLogger().severe("Unsupported reference data type: " + item.getClass().getName());
        }
        return null;
    }
   
    // =========================================
    // Site reference data - stored in Redis
    // =========================================
    private void processSitesData(CSVSitesData sitesItem) {
        redisService.saveHashValue(REDIS_SITES_KEY, sitesItem.getSiteReference(), sitesItem.getCourtName());
        // Logger.getAnonymousLogger().info("Stored site data: " + sitesItem.getSiteReference() + " -> " + sitesItem.getCourtName());
    }

    // ==================================================
    // Channel user reference data - stored in local map
    // ==================================================
    private void processChannelUserData(CSVChannelData channelDataItem) {
        channelUserDataMap
            .computeIfAbsent(channelDataItem.getChannelName(), k -> new ArrayList<>())
            .add(new String[]{channelDataItem.getChannelUser(), channelDataItem.getChannelUserEmail()});

        // Logger.getAnonymousLogger().info("Stored channel user data: " + channelDataItem.getChannelUser() + " -> " + channelDataItem.getChannelUserEmail());
    }


    public Map<String, List<String[]>> getChannelUserDataMap() {
        return channelUserDataMap;
    }
}
