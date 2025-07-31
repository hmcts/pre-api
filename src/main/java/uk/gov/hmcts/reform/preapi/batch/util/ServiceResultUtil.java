package uk.gov.hmcts.reform.preapi.batch.util;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;
import uk.gov.hmcts.reform.preapi.batch.entities.TestItem;

@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class ServiceResultUtil {
    public <T> ServiceResult<T> failure(String errorMessage, String category) {
        return new ServiceResult<>(errorMessage, category);
    }

    public <T> ServiceResult<T> success(T data) {
        return new ServiceResult<>(data);
    }

    public ServiceResult<TestItem> test(TestItem testItem, boolean isTest) {
        return new ServiceResult<>(testItem, isTest);
    }
}
