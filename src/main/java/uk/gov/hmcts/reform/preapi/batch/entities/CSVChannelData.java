package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CSVChannelData {
    @Setter
    private String channelName;
    @Setter
    private String channelUser;
    @Setter
    private String channelUserEmail;
    private String caseReference;

    @Override
    public String toString() {
        return "CSVChannelData{"
                + "channelName='" + channelName + '\''
                + ", channelUser='" + channelUser + '\''
                + ", channelUserEmail='" + channelUserEmail + '\''
                + ", caseReference='" + caseReference + '\''
                + '}';
    }
}
