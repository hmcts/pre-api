package uk.gov.hmcts.reform.preapi.batch.entities;

public class CSVChannelData {

    private String channelName;
    private String channelUser;
    private String channelUserEmail;
    private String caseReference;

    public CSVChannelData() {
    }
    
    public CSVChannelData(
        String channelName, 
        String channelUser, 
        String caseReference,
        String channelUserEmail
    ) {
        this.channelName = channelName;
        this.channelUser = channelUser;
        this.channelUserEmail = channelUserEmail;
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

    public String getChannelUserEmail() {
        return channelUserEmail;
    }

    public void setChannelUserEmail(String channelUserEmail) {
        this.channelUserEmail = channelUserEmail;
    }

    public String getCaseReference() {
        return caseReference;
    }

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
