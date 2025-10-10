package uk.gov.hmcts.reform.preapi.exception;

import com.azure.core.management.exception.ManagementException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.gov.hmcts.reform.preapi.dto.B2CErrorDTO;

import java.util.Arrays;
import java.util.HashMap;

@ControllerAdvice
@Slf4j
public class GlobalControllerExceptionHandler {

    private static final String MESSAGE = "message";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<String> notFoundExceptionHandler(final NotFoundException e) throws JsonProcessingException {
        log.info("Not found exception: {}", e.getMessage());
        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<String> conflictExceptionHandler(final ConflictException e) throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(PathPayloadMismatchException.class)
    ResponseEntity<String> pathPayloadMismatchExceptionHandler(final PathPayloadMismatchException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(UnknownServerException.class)
    ResponseEntity<String> unknownServerExceptionHandler(final UnknownServerException e)
        throws JsonProcessingException {
        log.error("Unknown server exception: {}", e.getMessage());
        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(ResourceInDeletedStateException.class)
    ResponseEntity<String> resourceInDeletedStateException(final ResourceInDeletedStateException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(RequestedPageOutOfRangeException.class)
    ResponseEntity<String> requestedPageOutOfRangeException(final RequestedPageOutOfRangeException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
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

        return new ResponseEntity<>(
            new ObjectMapper().writeValueAsString(error),
            responseHeaders,
            getStatus(e)
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<String> onMethodArgumentTypeMismatchException(final MethodArgumentTypeMismatchException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getCause().getMessage(), getStatus(e));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<String> onHttpMessageNotReadableException(final HttpMessageNotReadableException e)
        throws JsonProcessingException {

        String message = e.getMessage();

        // Try to extract a more meaningful error message from the cause
        Throwable cause = e.getCause();
        if (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.isEmpty()) {
                // For Jackson deserialization errors, extract the meaningful part
                if (causeMessage.contains("Cannot deserialize") ||
                    causeMessage.contains("Cannot construct") ||
                    causeMessage.contains("Unrecognized field") ||
                    causeMessage.contains("Missing required") ||
                    causeMessage.contains("Invalid value")) {
                    message = causeMessage;
                } else if (cause.getCause() != null && cause.getCause().getMessage() != null) {
                    // Sometimes the real error is nested deeper
                    message = cause.getCause().getMessage();
                }
            }
        }

        log.error("HttpMessageNotReadableException: {}", message);
        return getResponseEntity(message, getStatus(e));
    }

    @ExceptionHandler(ImmutableDataException.class)
    ResponseEntity<String> onImmutableDataException(final ImmutableDataException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(UnauditableTableException.class)
    ResponseEntity<String> onUnauditableTableException(final UnauditableTableException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(CaptureSessionNotDeletedException.class)
    ResponseEntity<String> onCaptureSessionNotDeletedException(final CaptureSessionNotDeletedException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> onIllegalArgumentException(final IllegalArgumentException e)
        throws JsonProcessingException {
        return getResponseEntity(
            "Unable to communicate with Azure. " + e.getMessage(),
            getStatus(e)
        );
    }

    @ExceptionHandler(FeignException.class)
    ResponseEntity<String> onFeignException(final FeignException e)
        throws JsonProcessingException {
        log.error("Feign Exception: {}", e.getMessage());
        return getResponseEntity(
            "Unable to connect to Media Service. " + e.getMessage(),
            getStatus(e)
        );
    }

    @ExceptionHandler(InterruptedException.class)
    ResponseEntity<String> onInterruptedException(final InterruptedException e)
        throws JsonProcessingException {
        log.error("An error occurred when trying to communicate with Media Service. {}", e.getMessage());
        log.error(Arrays.toString(e.getStackTrace()));
        return getResponseEntity(
            "An error occurred when trying to communicate with Media Service. " + e.getMessage(),
            getStatus(e)
        );
    }

    @ExceptionHandler(ManagementException.class)
    ResponseEntity<String> onManagementException(final ManagementException e) throws JsonProcessingException {
        log.error("An error occurred when trying to communicate with Azure Media Service. {}", e.getMessage());
        log.error(Arrays.toString(e.getStackTrace()));
        return getResponseEntity(
            "An error occurred when trying to communicate with Azure Media Service. " + e.getMessage(),
            getStatus(e)
        );
    }

    @ExceptionHandler(ResourceInWrongStateException.class)
    ResponseEntity<String> resourceInWrongStateException(final ResourceInWrongStateException e)
        throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<String> forbiddenExceptionHandler(final ForbiddenException e) throws JsonProcessingException {

        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(AssetFilesNotFoundException.class)
    ResponseEntity<String> amsAssetFilesNotFoundException(final AssetFilesNotFoundException e)
        throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(UnprocessableContentException.class)
    ResponseEntity<String> unprocessableContentException(final UnprocessableContentException e)
        throws JsonProcessingException {
        log.error("Unprocessable content exception: {}", e.getMessage());
        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ResponseEntity<String> invalidDataAccessApiUsageExceptionHandler(final InvalidDataAccessApiUsageException e)
        throws JsonProcessingException {
        return getResponseEntity(
            e.getMessage()
                .split("[\\[:]")[1]
                .replace("Could not resolve attribute", "Invalid sort parameter")
                .replace("' of '", "' for '")
                .trim(),
            getStatus(e)
        );
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<String> badRequestExceptionHandler(final BadRequestException e)
        throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    ResponseEntity<String> unsupportedMediaTypeException(final UnsupportedMediaTypeException e)
        throws JsonProcessingException {
        return getResponseEntity(e.getMessage(), getStatus(e));
    }

    @ExceptionHandler(B2CControllerException.class)
    ResponseEntity<String> b2cControllerExceptionHandler(final B2CControllerException e)
        throws JsonProcessingException {
        log.error("B2C Controller exception: {}", e.getMessage());

        var error = new B2CErrorDTO();
        var cause = e.getCause() != null ? e.getCause() : e;
        error.setUserMessage(cause.getMessage());
        // https://learn.microsoft.com/en-us/azure/active-directory-b2c/restful-technical-profile#returning-validation-error-message
        error.setStatus(HttpStatus.CONFLICT.value()); // Has to be 409...

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        return new ResponseEntity<>(
            new ObjectMapper().writeValueAsString(error),
            responseHeaders,
            HttpStatus.BAD_REQUEST // needs to be 4xx for B2C to render properly
        );
    }

    private static ResponseEntity<String> getResponseEntity(String message, HttpStatus status)
        throws JsonProcessingException {

        HashMap<String, String> error = new HashMap<>();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
        error.put(MESSAGE, message);
        return new ResponseEntity<>(
            new ObjectMapper().writeValueAsString(error),
            responseHeaders,
            status
        );
    }

    private static HttpStatus getStatus(Throwable t) {
        return switch (t) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            case AssetFilesNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case UnprocessableContentException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UnsupportedMediaTypeException ignored -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;

            case PathPayloadMismatchException ignored -> HttpStatus.BAD_REQUEST;
            case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
            case InvalidDataAccessApiUsageException ignored -> HttpStatus.BAD_REQUEST;
            case CaptureSessionNotDeletedException ignored -> HttpStatus.BAD_REQUEST;
            case UnauditableTableException ignored -> HttpStatus.BAD_REQUEST;
            case ImmutableDataException ignored -> HttpStatus.BAD_REQUEST;
            case RequestedPageOutOfRangeException ignored -> HttpStatus.BAD_REQUEST;
            case ResourceInDeletedStateException ignored -> HttpStatus.BAD_REQUEST;
            case HttpMessageNotReadableException ignored -> HttpStatus.BAD_REQUEST;
            case MethodArgumentNotValidException ignored -> HttpStatus.BAD_REQUEST;
            case MethodArgumentTypeMismatchException ignored -> HttpStatus.BAD_REQUEST;
            case ResourceInWrongStateException ignored -> HttpStatus.BAD_REQUEST;
            case B2CControllerException ignored -> HttpStatus.BAD_REQUEST;

            case IllegalArgumentException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;
            case ManagementException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;
            case FeignException ignored -> HttpStatus.INTERNAL_SERVER_ERROR;

            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }


}
