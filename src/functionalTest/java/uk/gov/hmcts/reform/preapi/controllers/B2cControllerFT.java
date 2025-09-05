package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.VerifyEmailRequestDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;

public class B2cControllerFT extends FunctionalTestBase {

    @Test
    void emailValidationError() throws JsonProcessingException {
        var request = new VerifyEmailRequestDTO();
        request.setEmail("test@test.com");
        request.setVerificationCode("123456");

        var response = doPostRequest("b2c/email-verification",
            OBJECT_MAPPER.writeValueAsString(request),
            null);

        assertResponseCode(response, NOT_FOUND.value());
        assertThat(response.body().jsonPath().getString("userMessage"))
            .isEqualTo("Not found: User: test@test.com");
    }
}
