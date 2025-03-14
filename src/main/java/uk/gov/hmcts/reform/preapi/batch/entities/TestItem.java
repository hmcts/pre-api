package uk.gov.hmcts.reform.preapi.batch.entities;

public class TestItem {
    private CSVArchiveListData archiveItem;
    private String reason;
    private boolean durationCheck;
    private int durationInSeconds;
    private boolean keywordCheck;
    private String keywordFound;

    public TestItem(
        CSVArchiveListData archiveItem,
        String reason,
        boolean durationCheck,
        int durationInSeconds,
        boolean keywordCheck,
        String keywordFound
    ) {
        this.archiveItem = archiveItem;
        this.reason = reason;
        this.durationCheck = durationCheck;
        this.durationInSeconds = durationInSeconds;
        this.keywordCheck = keywordCheck;
        this.keywordFound = keywordFound;
    }

    public CSVArchiveListData getArchiveItem() {
        return this.archiveItem;
    }

    public String getReason() {
        return reason;
    }

    public boolean isDurationCheck() {
        return durationCheck;
    }

    public int getDurationInSeconds() {
        return durationInSeconds;
    }

    public boolean isKeywordCheck() {
        return keywordCheck;
    }

    public String getKeywordFound() {
        return keywordFound;
    }

   
}
