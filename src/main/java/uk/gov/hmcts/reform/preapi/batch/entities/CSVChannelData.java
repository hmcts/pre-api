package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CSVChannelData {

    private String channelName;
    private String channelUser;
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
