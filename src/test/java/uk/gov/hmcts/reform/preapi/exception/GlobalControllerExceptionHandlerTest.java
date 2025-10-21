package uk.gov.hmcts.reform.preapi.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalControllerExceptionHandlerTest {

    private GlobalControllerExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalControllerExceptionHandler();
    }

    @DisplayName("Should handle HttpMessageNotReadableException with simple message")
    @Test
    void testHttpMessageNotReadableExceptionSimpleMessage() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error: Unexpected character");

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("JSON parse error: Unexpected character");
        assertThat(response.getHeaders().getContentType().toString()).contains("application/json");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with null cause")
    @Test
    void testHttpMessageNotReadableExceptionNullCause() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("Failed to read request");
        when(exception.getCause()).thenReturn(null);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Failed to read request");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with 'Cannot deserialize' cause")
    @Test
    void testHttpMessageNotReadableExceptionCannotDeserialize() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("Cannot deserialize value of type `java.util.UUID` from String");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Cannot deserialize value of type `java.util.UUID` from String");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with 'Cannot construct' cause")
    @Test
    void testHttpMessageNotReadableExceptionCannotConstruct() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("Cannot construct instance of `SomeClass` (no Creators)");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Cannot construct instance of `SomeClass` (no Creators)");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with 'Unrecognized field' cause")
    @Test
    void testHttpMessageNotReadableExceptionUnrecognizedField() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("Unrecognized field \"invalidField\" (class SomeClass)");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Unrecognized field \\\"invalidField\\\" (class SomeClass)");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with 'Missing required' cause")
    @Test
    void testHttpMessageNotReadableExceptionMissingRequired() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("Missing required creator property 'id'");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Missing required creator property 'id'");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with 'Invalid value' cause")
    @Test
    void testHttpMessageNotReadableExceptionInvalidValue() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("Invalid value for enum type");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid value for enum type");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with nested cause")
    @Test
    void testHttpMessageNotReadableExceptionNestedCause() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var nestedCause = new RuntimeException("Nested error: Invalid format");
        var cause = new RuntimeException("Wrapper exception", nestedCause);

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Nested error: Invalid format");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with cause but null message")
    @Test
    void testHttpMessageNotReadableExceptionCauseNullMessage() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException((String) null);

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("JSON parse error");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with cause but empty message")
    @Test
    void testHttpMessageNotReadableExceptionCauseEmptyMessage() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("JSON parse error");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with non-matching cause message")
    @Test
    void testHttpMessageNotReadableExceptionNonMatchingCause() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var nestedCause = new RuntimeException("Some nested error");
        var cause = new RuntimeException("Some other error", nestedCause);

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Some nested error");
    }

    @DisplayName("Should handle HttpMessageNotReadableException with non-matching cause and null nested cause")
    @Test
    void testHttpMessageNotReadableExceptionNonMatchingCauseNullNested() throws JsonProcessingException {
        var exception = mock(HttpMessageNotReadableException.class);
        var cause = new RuntimeException("Some other error");

        when(exception.getMessage()).thenReturn("JSON parse error");
        when(exception.getCause()).thenReturn(cause);

        var response = handler.onHttpMessageNotReadableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("JSON parse error");
    }

    @DisplayName("Should handle ForbiddenException")
    @Test
    void testForbiddenException() throws JsonProcessingException {
        var exception = new ForbiddenException("Access denied");

        var response = handler.forbiddenExceptionHandler(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Access denied");
    }

    @DisplayName("Should handle NotFoundException")
    @Test
    void testNotFoundException() throws JsonProcessingException {
        var exception = new NotFoundException("Resource not found");

        var response = handler.notFoundExceptionHandler(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Resource not found");
    }

    @DisplayName("Should handle BadRequestException")
    @Test
    void testBadRequestException() throws JsonProcessingException {
        var exception = new BadRequestException("Invalid request");

        var response = handler.badRequestExceptionHandler(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid request");
    }
}
