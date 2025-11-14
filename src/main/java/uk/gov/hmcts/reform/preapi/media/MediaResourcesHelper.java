package uk.gov.hmcts.reform.preapi.media;

import java.util.UUID;

@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class MediaResourcesHelper {

    public static String getSanitisedLiveEventId(UUID liveEventId) {
        return liveEventId.toString().replace("-", "");
    }
}
