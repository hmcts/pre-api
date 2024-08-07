package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkTransformOutput {
    private MkBuiltInPreset preset;
    private MkTransformPriority relativePriority;

    public enum MkTransformPriority {
        Low,
        Normal,
        High
    }
}
