package uk.gov.hmcts.reform.preapi.alerts;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(fluent = true)
public class SlackMessageJsonOptions {
    private boolean showEnvironment;
    private boolean showIcons;
}
