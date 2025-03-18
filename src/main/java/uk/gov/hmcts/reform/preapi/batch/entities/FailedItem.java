package uk.gov.hmcts.reform.preapi.batch.entities;

public class FailedItem {

    private Object item;
    private String reason;
    private String failureCategory;

    public FailedItem(
        Object item, 
        String reason,
        String failureCategory
    ) {
        this.item = item;
        this.reason = reason;
        this.failureCategory = failureCategory;
    }

    public Object getItem() {
        return item;
    }

    public String getReason() {
        return reason;
    }

    public String getFailureCategory() {
        return failureCategory;
    }

    public String getFileName() {
        if (item instanceof CSVArchiveListData) {
            return ((CSVArchiveListData) item).getFileName();
        } else if (item instanceof ExtractedMetadata) {
            return ((ExtractedMetadata) item).getFileName();
        }
        return "Unknown File";
    }
}
