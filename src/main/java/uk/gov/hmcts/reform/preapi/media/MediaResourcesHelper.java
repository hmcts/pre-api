package uk.gov.hmcts.reform.preapi.media;

import java.util.UUID;

public abstract class MediaResourcesHelper {

    public static String getSanitisedLiveEventId(UUID liveEventId) {
        return liveEventId.toString().replace("-", "");
    }
}
