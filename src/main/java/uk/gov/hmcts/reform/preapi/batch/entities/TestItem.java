package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TestItem {
    private CSVArchiveListData archiveItem;
    private String reason;
    private boolean durationCheck;
    private int durationInSeconds;
    private boolean keywordCheck;
    private String keywordFound;
    private boolean regexFailure;
}
