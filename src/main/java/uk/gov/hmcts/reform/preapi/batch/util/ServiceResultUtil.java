package uk.gov.hmcts.reform.preapi.batch.util;

import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

public final class ServiceResultUtil {

    private ServiceResultUtil() {
    }

    public static <T> ServiceResult<T> failure(String errorMessage, String category) {
        return new ServiceResult<>(errorMessage, category);
    }

    public static <T> ServiceResult<T> success(T data) {
        return new ServiceResult<>(data);
    }

    public static ServiceResult<TestItem> test(TestItem testItem, boolean isTest) {
        return new ServiceResult<>(testItem, isTest);
    }
}

