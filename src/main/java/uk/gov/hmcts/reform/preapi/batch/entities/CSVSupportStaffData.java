package uk.gov.hmcts.reform.preapi.batch.entities;

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
