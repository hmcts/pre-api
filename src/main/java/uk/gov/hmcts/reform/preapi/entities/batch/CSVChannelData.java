package uk.gov.hmcts.reform.preapi.entities.batch;

public class CSVChannelData {

    private String channelName;
    private String channelUser;
    private String caseReference;

    public CSVChannelData() {
    }
    
    public CSVChannelData(String channelName, String channelUser, String caseReference) {
        this.channelName = channelName;
        this.channelUser = channelUser;
        this.caseReference = caseReference;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelUser() {
        return channelUser;
    }

    public void setChannelUser(String channelUser) {
        this.channelUser = channelUser;
    }

    public String getCaseReference() {
        return caseReference;
    }

}
