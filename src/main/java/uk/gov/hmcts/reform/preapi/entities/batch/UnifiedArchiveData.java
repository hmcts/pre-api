package uk.gov.hmcts.reform.preapi.entities.batch;

public class UnifiedArchiveData {

    private String displayName;
    private String createTime;
    private int duration;
    private String additionalData;

    public UnifiedArchiveData() {
    }

    public UnifiedArchiveData(String displayName, String createTime, int duration, String additionalData) {
        this.displayName = displayName;
        this.createTime = createTime;
        this.duration = duration;
        this.additionalData = additionalData;
    }


    public String getArchiveName() {
        return displayName;
    }

    public void setArchiveName(String displayName) {
        this.displayName = displayName;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(String additionalData) {
        this.additionalData = additionalData;
    }

    public String getArchiveNameNoExt() {
        if (displayName == null || displayName.isEmpty()) {
            return displayName;
        }
        int lastDotIndex = displayName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return displayName;
        }
        return displayName.substring(0, lastDotIndex);
    }

    @Override
    public String toString() {
        return "UnifiedArchiveData{" 
            + "displayName='" + displayName + '\'' 
            + ", createTime='" + createTime + '\'' 
            + ", duration=" + duration 
            + ", additionalData='" + additionalData + '\'' 
            + '}';
    }
}
