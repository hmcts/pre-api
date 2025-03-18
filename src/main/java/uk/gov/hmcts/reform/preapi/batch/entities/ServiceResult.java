package uk.gov.hmcts.reform.preapi.batch.entities;

public class ServiceResult<T> {
    private T data;
    private String errorMessage;
    private String category;
    public boolean success;
    public boolean isTest;
    private TestItem testItem;

    // success result
    public ServiceResult(T data) {
        this.data = data;
        this.success = true;
        this.isTest = false; 
    }

    // failure result
    public ServiceResult(String errorMessage, String category) {
        this.errorMessage = errorMessage;
        this.category = category;
        this.success = false;
        this.isTest = false;
    }

    // test result
    public ServiceResult(TestItem test, boolean isTest) {
        this.isTest = isTest;
        this.testItem = test;
        this.success = false;
    }

    public T getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCategory() {
        return category;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isTest() {
        return isTest;
    }

    public void setData(T data) {
        this.data = data;
        this.success = true;
        this.isTest = false;
    }

    public void setErrorMessage(String errorMessage, boolean isTest) {
        this.errorMessage = errorMessage;
        this.success = false;
        this.isTest = true;
    }

    public void setFailureCategory(String category) {
        this.category = category;
    }

    public TestItem getTestItem() { 
        return testItem;
    }
    
}
