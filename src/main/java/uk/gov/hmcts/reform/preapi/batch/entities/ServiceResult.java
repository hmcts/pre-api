package uk.gov.hmcts.reform.preapi.batch.entities;

public class ServiceResult<T> {
    private T data;
    private String errorMessage;
    private String category;

    public ServiceResult(T data) {
        this.data = data;
    }

    public ServiceResult(String errorMessage, String category) {
        this.errorMessage = errorMessage;
        this.category = category;
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

    public void setData(T data) {
        this.data = data;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setFailureCategory(String category) {
        this.category = category;
    }
    
}
