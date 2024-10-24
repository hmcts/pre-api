package uk.gov.hmcts.reform.preapi.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static uk.gov.hmcts.reform.preapi.exception.GlobalControllerExceptionHandler.getResponseEntity;


@ControllerAdvice
@ConditionalOnExpression("${testing-support-endpoints.enabled:false}")
public class TestingSupportExceptionHandler  {

    @ExceptionHandler(Exception.class)
    ResponseEntity<String> exceptionHandler(final Exception e) throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
