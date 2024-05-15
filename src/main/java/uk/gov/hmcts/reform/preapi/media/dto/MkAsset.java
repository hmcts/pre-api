package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkAsset {
    private String name;
    private MkAssetProperties properties;
}
