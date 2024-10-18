package uk.gov.hmcts.reform.preapi.entities.batch;

public class CSVSupportStaffData {

    private String userName;

    public CSVSupportStaffData() {
    }
    
    public CSVSupportStaffData(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
