package uk.gov.hmcts.reform.preapi.batch.util;

import uk.gov.hmcts.reform.preapi.batch.entities.ServiceResult;

public final class ServiceResultUtil {

    private ServiceResultUtil() {
    }

    public static <T> ServiceResult<T> failure(String errorMessage) {
        return new ServiceResult<>(errorMessage);
    }

    public static <T> ServiceResult<T> success(T data) {
        return new ServiceResult<>(data);
    }
}

