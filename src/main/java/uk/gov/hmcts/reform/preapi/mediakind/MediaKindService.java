package uk.gov.hmcts.reform.preapi.mediakind;

import uk.gov.hmcts.reform.preapi.controllers.response.StreamingLinkResponse;

public interface MediaKindService {

    String getStreamingLocatorName(String assetName);

    StreamingLinkResponse getStreamingPathsForStreamingLocator(String locatorName);
}
