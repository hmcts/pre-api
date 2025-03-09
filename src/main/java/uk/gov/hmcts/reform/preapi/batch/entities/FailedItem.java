package uk.gov.hmcts.reform.preapi.batch.entities;

public class FailedItem {

    private CSVArchiveListData archiveItem;
    private String reason;
    private String failureCategory;

    public FailedItem(
        CSVArchiveListData archiveItem, 
        String reason,
        String failureCategory
    ) {
        this.archiveItem = archiveItem;
        this.reason = reason;
        this.failureCategory = failureCategory;
    }

    public CSVArchiveListData getArchiveItem() {
        return this.archiveItem;
    }

    public String getReason() {
        return reason;
    }

    public String getFailureCategory() {
        return failureCategory;
    }
}
