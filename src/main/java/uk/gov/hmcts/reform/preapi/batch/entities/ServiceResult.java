package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.Getter;

@Getter
public class ServiceResult<T> {
    private T data;
    private String errorMessage;
    private String category;
    private boolean success;
    private boolean test;
    private TestItem testItem;

    // success result
    public ServiceResult(T data) {
        this.data = data;
        this.success = true;
        this.test = false;
    }

    // failure result
    public ServiceResult(String errorMessage, String category) {
        this.errorMessage = errorMessage;
        this.category = category;
        this.success = false;
        this.test = false;
    }

    // test result
    public ServiceResult(TestItem test, boolean isTest) {
        this.test = isTest;
        this.testItem = test;
        this.success = false;
    }

    public void setData(T data) {
        this.data = data;
        this.success = true;
        this.test = false;
    }

    public static <T> ServiceResult<T> createSuccessResult(T data) {
        return new ServiceResult<>(data);
    }

    public static <T> ServiceResult<T> createErrorResult(String message, String category) {
        return new ServiceResult<>(message, category);
    }

    public static ServiceResult<TestItem> createTestResult(TestItem testItem) {
        return new ServiceResult<>(testItem, true);
    }
}
