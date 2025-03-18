package uk.gov.hmcts.reform.preapi.batch.entities;

public class CSVExemptionListData {

    private String archiveName;
    private String createTime;
    private int duration;
    private String courtReference;
    private String urn;
    private String exhibitReference;
    private String defendantName;
    private String witnessName;
    private String recordingVersion;
    private int recordingVersionNumber;
    private String fileExtension;
    private String fileName;
    private String fileSize;
    private String reason;
    private String addedBy; 


    public CSVExemptionListData() {}

    public CSVExemptionListData(String archiveName, String createTime, int duration, String courtReference, 
                            String urn, String exhibitReference, String defendantName, String witnessName, 
                            String recordingVersion, int recordingVersionNumber, String fileExtension, 
                            String fileName, String fileSize, String reason, String addedBy) {
        this.archiveName = archiveName;
        this.createTime = createTime;
        this.duration = duration;
        this.courtReference = courtReference;
        this.urn = urn;
        this.exhibitReference = exhibitReference;
        this.defendantName = defendantName;
        this.witnessName = witnessName;
        this.recordingVersion = recordingVersion;
        this.recordingVersionNumber = recordingVersionNumber;
        this.fileExtension = fileExtension;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.reason = reason;
        this.addedBy = addedBy;
    }

    public String getArchiveName() {
        return archiveName;
    }

    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
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

    public String getCourtReference() {
        return courtReference;
    }

    public void setCourtReference(String courtReference) {
        this.courtReference = courtReference;
    }

    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public String getExhibitReference() {
        return exhibitReference;
    }

    public void setExhibitReference(String exhibitReference) {
        this.exhibitReference = exhibitReference;
    }

    public String getDefendantName() {
        return defendantName;
    }

    public void setDefendantName(String defendantName) {
        this.defendantName = defendantName;
    }

    public String getWitnessName() {
        return witnessName;
    }

    public void setWitnessName(String witnessName) {
        this.witnessName = witnessName;
    }

    public String getRecordingVersion() {
        return recordingVersion;
    }

    public void setRecordingVersion(String recordingVersion) {
        this.recordingVersion = recordingVersion;
    }

    public int getRecordingVersionNumber() {
        return recordingVersionNumber;
    }

    public void setRecordingVersionNumber(int recordingVersionNumber) {
        this.recordingVersionNumber = recordingVersionNumber;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return "CSVExemptionListData{" 
                + "archiveName='" + archiveName + '\'' 
                + ", createTime='" + createTime + '\'' 
                + ", duration=" + duration 
                + ", courtReference='" + courtReference + '\'' 
                + ", urn='" + urn + '\'' 
                + ", exhibitReference='" + exhibitReference + '\''
                + ", defendantName='" + defendantName + '\'' 
                + ", witnessName='" + witnessName + '\'' 
                + ", recordingVersion='" + recordingVersion + '\'' 
                + ", recordingVersionNumber=" + recordingVersionNumber 
                + ", reason='" + reason + '\'' 
                + ", addedBy='" + addedBy + '\'' 
                + ", fileExtension='" + fileExtension + '\'' 
                + ", fileName='" + fileName + '\'' 
                + ", fileSize='" + fileSize + '\'' 
                + '}';
    }
}
