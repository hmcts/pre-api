package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MkStreamingLocatorList {
    private List<MkStreamingLocator> streamingLocators;
    private MkGetListResponse.Supplemental supplemental;
}
