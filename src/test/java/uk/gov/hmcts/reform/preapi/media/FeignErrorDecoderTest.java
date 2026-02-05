package uk.gov.hmcts.reform.preapi.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import lombok.Cleanup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkErrorResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FeignErrorDecoderTest {

    private static final FeignErrorDecoder DECODER = new FeignErrorDecoder();

    private static final Request REQUEST = Request.create(
        Request.HttpMethod.GET,
        "localhost",
        Collections.emptyMap(),
        Request.Body.create(new byte[0]),
        null
    );

    @DisplayName("Test for 404")
    @Test
    void test404() throws JsonProcessingException {
        @Cleanup var response = Response.builder()
                                .request(REQUEST)
                                .headers(Collections.singletonMap("AcceptTest", Collections.singletonList("Yes")))
                                .status(HttpStatus.NOT_FOUND.value())
                                .reason("Could not find")
                                .body(getMkErrorResponseString(getMkErrorResponse(404)).getBytes())
                                .build();

        assertThat(decode(response))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Not found: Not Found");
    }

    @DisplayName("Test for 400")
    @Test
    @SuppressWarnings("checkstyle:linelength")
    void test400() throws JsonProcessingException {
        @Cleanup var response = Response.builder()
                               .request(REQUEST)
                               .headers(Collections.singletonMap("AcceptTest", Collections.singletonList("Yes")))
                               .status(HttpStatus.BAD_REQUEST.value())
                               .reason("Bad request")
                               .body(getMkErrorResponseString(getMkErrorResponse(400)).getBytes())
                               .build();

        assertThat(decode(response))
            .isInstanceOf(FeignException.class)
            .hasMessage("[400 Bad request] during [GET] to [localhost] [methodKey]: [{\"error\":{\"code\":\"400\",\"detail\":\"Bad Request\"},\"ref\":\"12345\",\"status\":400}]");
    }

    @DisplayName("Test for 400 which should be a 409")
    @Test
    void test400to409() throws JsonProcessingException {
        @Cleanup var response = Response.builder()
                               .request(REQUEST)
                               .headers(Collections.singletonMap("AcceptTest", Collections.singletonList("Yes")))
                               .status(HttpStatus.BAD_REQUEST.value())
                               .reason("Bad request")
                               .body(getMkErrorResponseString(getMkErrorResponse(409)).getBytes())
                               .build();

        assertThat(decode(response))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Conflict: something something already exists.");
    }

    @DisplayName("Test for 409")
    @Test
    void test409() throws JsonProcessingException {
        @Cleanup var response = Response.builder()
            .request(REQUEST)
            .headers(Collections.singletonMap("AcceptTest", Collections.singletonList("Yes")))
            .status(HttpStatus.CONFLICT.value())
            .reason("Conflict")
            .body(getMkErrorResponseString(getMkErrorResponse(409)).getBytes())
            .build();

        assertThat(decode(response))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Conflict: something something already exists.");
    }

    private Exception decode(Response response) {
        return DECODER.decode("methodKey", response);
    }

    private MkErrorResponse getMkErrorResponse(int errCode) throws JsonProcessingException {
        var response = new MkErrorResponse();
        var error = new MkErrorResponse.MkError();

        switch (errCode) {
            case 404:
                error.setCode("404");
                error.setDetail("Not Found");
                break;
            case 409:
                error.setCode("409");
                error.setDetail("something something already exists.");
                break;
            default:
                error.setCode("400");
                error.setDetail("Bad Request");
                break;
        }

        response.setError(error);
        response.setRef("12345");
        response.setStatus(errCode);

        return response;
    }

    private String getMkErrorResponseString(MkErrorResponse response) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(response);
    }

}
