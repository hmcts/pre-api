package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkStreamingLocator {
    private MkStreamingLocatorProperties properties;
}
