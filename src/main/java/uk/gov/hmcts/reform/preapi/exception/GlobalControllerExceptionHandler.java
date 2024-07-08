package uk.gov.hmcts.reform.preapi.exception;

import com.azure.core.management.exception.ManagementException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.MsalServiceException;
import feign.FeignException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final String MESSAGE = "message";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private static final Logger logger = Logger.getLogger(GlobalControllerExceptionHandler.class.getName());

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<String> notFoundExceptionHandler(final NotFoundException e) throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<String> conflictExceptionHandler(final ConflictException e) throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PathPayloadMismatchException.class)
    ResponseEntity<String> pathPayloadMismatchExceptionHandler(final PathPayloadMismatchException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnknownServerException.class)
    ResponseEntity<String> unknownServerExceptionHandler(final UnknownServerException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResourceInDeletedStateException.class)
    ResponseEntity<String> resourceInDeletedStateException(final ResourceInDeletedStateException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RequestedPageOutOfRangeException.class)
    ResponseEntity<String> requestedPageOutOfRangeException(final RequestedPageOutOfRangeException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<String> onMethodArgumentTypeMismatchException(final MethodArgumentTypeMismatchException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getCause().getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<String> onHttpMessageNotReadableException(final HttpMessageNotReadableException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ImmutableDataException.class)
    ResponseEntity<String> onImmutableDataException(final ImmutableDataException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauditableTableException.class)
    ResponseEntity<String> onUnauditableTableException(final UnauditableTableException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RecordingNotDeletedException.class)
    ResponseEntity<String> onRecordingNotDeletedException(final RecordingNotDeletedException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> onIllegalArgumentException(final IllegalArgumentException e)
        throws JsonProcessingException {
        return getResponseEntity(
            "Unable to communicate with Azure. " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(FeignException.class)
    ResponseEntity<String> onFeignException(final FeignException e)
        throws JsonProcessingException {
        return getResponseEntity(
            "Unable to connect to Media Service. " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(InterruptedException.class)
    ResponseEntity<String> onInterruptedException(final InterruptedException e)
        throws JsonProcessingException {
        logger.severe("An error occurred when trying to communicate with Media Service. " + e.getMessage());
        logger.severe(Arrays.toString(e.getStackTrace()));
        return getResponseEntity(
            "An error occurred when trying to communicate with Media Service. " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(ManagementException.class)
    ResponseEntity<String> onManagementException(final ManagementException e) throws JsonProcessingException {
        logger.severe("An error occurred when trying to communicate with Azure Media Service. " + e.getMessage());
        logger.severe(Arrays.toString(e.getStackTrace()));
        return getResponseEntity(
            "An error occurred when trying to communicate with Azure Media Service. " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(MsalServiceException.class)
    ResponseEntity<String> onMsalServiceException(final MsalServiceException e) throws JsonProcessingException {
        logger.severe("An error occurred when trying to communicate with Azure Media Service. " + e.getMessage());
        logger.severe(Arrays.toString(e.getStackTrace()));
        return getResponseEntity(
            "An error occurred when trying to communicate with Azure Media Service. " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(MediaKindException.class)
    ResponseEntity<String> onMediaKindException(final MediaKindException e) throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AMSLiveEventNotFoundException.class)
    ResponseEntity<String> amsLiveEventNotFoundException(final AMSLiveEventNotFoundException e)
        throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AMSLiveEventNotRunningException.class)
    ResponseEntity<String> amsLiveEventNotRunningException(final AMSLiveEventNotRunningException e)
        throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResourceInWrongStateException.class)
    ResponseEntity<String> resourceInWrongStateException(final ResourceInWrongStateException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    private static ResponseEntity<String> getResponseEntity(String message, HttpStatus status)
        throws JsonProcessingException {

        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, message);
        return new ResponseEntity<>(new ObjectMapper().writeValueAsString(error),
                                    responseHeaders,
                                    status);
    }
}
