package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FailedItem {
    private IArchiveData item;
    private String reason;
    private String failureCategory;

    public String getFileName() {
        return item.getFileName();
    }
}
