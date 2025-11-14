package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class TestItem {
    private MigrationRecord archiveItem;
    private String reason;
    private boolean durationCheck;
    private int durationInSeconds;
    private boolean keywordCheck;
    private String keywordFound;
    private boolean regexFailure;
}
