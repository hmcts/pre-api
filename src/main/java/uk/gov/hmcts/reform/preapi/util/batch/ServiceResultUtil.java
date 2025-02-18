package uk.gov.hmcts.reform.preapi.util.batch;

import uk.gov.hmcts.reform.preapi.entities.batch.ServiceResult;

public class ServiceResultUtil {

    private ServiceResultUtil() {
    }

    public static <T> ServiceResult<T> createFailureReponse(String errorMessage) {
        return new ServiceResult<>(errorMessage);
    }

    public static <T> ServiceResult<T> createSuccessResponse(T data) {
        return new ServiceResult<>(data);
    }
}

