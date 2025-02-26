package uk.gov.hmcts.reform.preapi.batch.entities;

public class ServiceResult<T> {
    private T data;
    private String errorMessage;

    public ServiceResult(T data) {
        this.data = data;
    }

    public ServiceResult(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public T getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setData(T data) {
        this.data = data;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
}
