package uk.gov.hmcts.reform.preapi.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkErrorResponse;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder errorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() != null) {
            MkErrorResponse errorResponse;
            try (InputStream bodyIs = response.body().asInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                errorResponse = mapper.readValue(bodyIs, MkErrorResponse.class);
            } catch (IOException e) {
                return new Exception(e.getMessage());
            }
            if (response.status() == HttpStatus.NOT_FOUND.value()) {
                return new NotFoundException(errorResponse.getError().getDetail() != null
                                                 ? errorResponse.getError().getDetail()
                                                 : "Not found");
            }
            // MK API sometimes returns a 400 instead of a 409 when the live event already exists
            if ((response.status() == HttpStatus.BAD_REQUEST.value()
                && errorResponse.getError().getDetail().contains(" already exists."))
                || response.status() == HttpStatus.CONFLICT.value()) {
                return new ConflictException(errorResponse.getError().getDetail());
            }
        }

        return errorDecoder.decode(methodKey, response);
    }
}
