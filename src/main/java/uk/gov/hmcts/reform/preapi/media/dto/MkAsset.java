package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Data;

@Data
public class MkAsset {
    private String name;
    private MkAssetProperties properties;
}
