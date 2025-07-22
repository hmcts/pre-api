package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import uk.gov.hmcts.reform.preapi.dto.VerifyEmailRequestDTO;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class B2CControllerFT {

    @LocalServerPort
    private int port;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = String.format("http://localhost:%s", port);
    }

    @Test
    void functionalTest() throws JsonProcessingException {
        var request = new VerifyEmailRequestDTO();
        request.setEmail("an@email.com");
        request.setVerificationCode("123456");

        final var response = given()
            .relaxedHTTPSValidation()
            .when()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body(OBJECT_MAPPER.writeValueAsString(request))
            .post("/b2c/email-verification")

            .thenReturn();

        assertThat(response.statusCode()).isEqualTo(OK.value());
    }
}
