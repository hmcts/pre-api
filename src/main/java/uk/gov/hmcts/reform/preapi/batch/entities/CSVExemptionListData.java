package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
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
