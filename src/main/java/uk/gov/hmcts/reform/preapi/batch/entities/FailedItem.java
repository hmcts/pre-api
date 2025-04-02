package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FailedItem {
    private Object item;
    private String reason;
    private String failureCategory;

    public String getFileName() {
        if (item instanceof CSVArchiveListData csvArchiveListData) {
            return (csvArchiveListData).getFileName();
        } else if (item instanceof ExtractedMetadata extractedMetadata) {
            return (extractedMetadata).getFileName();
        }
        return "Unknown File";
    }
}
