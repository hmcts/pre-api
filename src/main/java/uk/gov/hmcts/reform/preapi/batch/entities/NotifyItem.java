package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NotifyItem {
    private String notification;
    private ExtractedMetadata extractedMetadata;

    @Override
    public String toString() {
        return "NotifyItem{"
                + "extractedMetadata=" + extractedMetadata
                + '}';
    }

}
