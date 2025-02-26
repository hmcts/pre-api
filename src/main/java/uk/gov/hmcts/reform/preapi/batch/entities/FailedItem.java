package uk.gov.hmcts.reform.preapi.batch.entities;

public class FailedItem {

    private CSVArchiveListData archiveItem;
    private String reason;

    public FailedItem(CSVArchiveListData archiveItem, String reason) {
        this.archiveItem = archiveItem;
        this.reason = reason;
    }

    public CSVArchiveListData getArchiveItem() {
        return this.archiveItem;
    }

    public String getReason() {
        return reason;
    }
}
