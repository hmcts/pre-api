package uk.gov.hmcts.reform.preapi.entities.batch;

public class TestItem {
    private boolean isTest;
    private String reason;

    public TestItem(boolean isTest, String reason) {
        this.isTest = isTest;
        this.reason = reason;
    }

    public boolean isTest() {
        return isTest;
    }

    public String getReason() {
        return reason;
    }
}



