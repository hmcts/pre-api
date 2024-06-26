package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkAssetProperties {
    private String description;
    private String container;
    private String storageAccountName;
}
