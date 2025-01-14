package uk.gov.hmcts.reform.preapi.entities.batch;

public class FailedItemXML {

    private UnifiedArchiveData archiveItem;
    private String reason;

    public FailedItemXML(UnifiedArchiveData archiveItem, String reason) {
        this.archiveItem = archiveItem;
        this.reason = reason;
    }

    public UnifiedArchiveData getArchiveItem() {
        return this.archiveItem;
    }

    public String getReason() {
        return reason;
    }
}


