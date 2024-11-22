package uk.gov.hmcts.reform.preapi.entities.batch;

public class CSVArchiveListData {

    private String archiveName;
    private String description;
    private String createTime;
    private int duration;
    private String owner;
    private String videoType;
    private String audioType;
    private String contentType;
    private String farEndAddress;

    public CSVArchiveListData() {
    }

    public CSVArchiveListData(String archiveName, String description, String createTime, int duration, 
                            String owner, String videoType, String audioType, String contentType, 
                            String farEndAddress) {
        this.archiveName = archiveName;
        this.description = description;
        this.createTime = createTime;
        this.duration = duration;
        this.owner = owner;
        this.videoType = videoType;
        this.audioType = audioType;
        this.contentType = contentType;
        this.farEndAddress = farEndAddress;
    }

    public String getArchiveName() {
        return archiveName;
    }

    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getVideoType() {
        return videoType;
    }

    public void setVideoType(String videoType) {
        this.videoType = videoType;
    }

    public String getAudioType() {
        return audioType;
    }

    public void setAudioType(String audioType) {
        this.audioType = audioType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFarEndAddress() {
        return farEndAddress;
    }

    public void setFarEndAddress(String farEndAddress) {
        this.farEndAddress = farEndAddress;
    }

    public String getArchiveNameNoExt() {
        if (archiveName == null || archiveName.isEmpty()) {
            return archiveName;
        }
        int lastDotIndex = archiveName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return archiveName;
        }
        return archiveName.substring(0, lastDotIndex);
    }

}
