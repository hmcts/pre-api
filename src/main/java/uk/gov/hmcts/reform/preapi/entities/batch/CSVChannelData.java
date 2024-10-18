package uk.gov.hmcts.reform.preapi.entities.batch;

public class CSVChannelData {

    private String channelName;
    private String channelUser;

    public CSVChannelData() {
    }
    
    public CSVChannelData(String channelName, String channelUser) {
        this.channelName = channelName;
        this.channelUser = channelUser;
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

}
