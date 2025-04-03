package uk.gov.hmcts.reform.preapi.batch.entities;

public class NotifyItem {
    private String notification;
    private ExtractedMetadata extractedMetadata;

    public NotifyItem() {
    }

    public NotifyItem(String notification, ExtractedMetadata extractedMetadata) {
        this.notification = notification;
        this.extractedMetadata = extractedMetadata;
    }

    public String getNotification() {
        return notification;
    }

    public void setNotification(String notification) {
        this.notification = notification;
    }

    public ExtractedMetadata getExtractedMetadata() {
        return extractedMetadata;
    }

    public void setExtractedMetadata(ExtractedMetadata extractedMetadata) {
        this.extractedMetadata = extractedMetadata;
    }

    @Override
    public String toString() {
        return "NotifyItem{" 
                + "extractedMetadata=" + extractedMetadata 
                + '}';
    }

}
