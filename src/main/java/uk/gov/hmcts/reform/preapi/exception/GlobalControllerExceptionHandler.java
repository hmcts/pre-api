package uk.gov.hmcts.reform.preapi.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final String MESSAGE = "message";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<String> notFoundExceptionHandler(final NotFoundException ex) throws JsonProcessingException {
        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, ex.getMessage());
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PathPayloadMismatchException.class)
    ResponseEntity<String> pathPayloadMismatchExceptionHandler(final PathPayloadMismatchException ex)
        throws JsonProcessingException {
        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, ex.getMessage());
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnknownServerException.class)
    ResponseEntity<String> unknownServerExceptionHandler(final UnknownServerException ex)
        throws JsonProcessingException {
        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, ex.getMessage());
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResourceInDeletedStateException.class)
    ResponseEntity<String> resourceInDeletedStateException(final ResourceInDeletedStateException ex)
        throws JsonProcessingException {
        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, ex.getMessage());
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RequestedPageOutOfRangeException.class)
    ResponseEntity<String> requestedPageOutOfRangeException(final RequestedPageOutOfRangeException ex)
        throws JsonProcessingException {
        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, ex.getMessage());
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<String> onMethodArgumentNotValidException(final MethodArgumentNotValidException e)
        throws JsonProcessingException {

        var error = new HashMap<String, String>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        for (var fieldError : e.getBindingResult().getFieldErrors()) {
            error.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    HttpStatus.BAD_REQUEST);
    }
}
