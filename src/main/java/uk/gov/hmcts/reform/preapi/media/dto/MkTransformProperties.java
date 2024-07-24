package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MkTransformProperties {
    private String description;
    private List<MkTransformOutput> outputs;
}
