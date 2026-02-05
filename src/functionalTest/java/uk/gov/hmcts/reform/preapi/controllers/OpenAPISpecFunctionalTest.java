package uk.gov.hmcts.reform.preapi.controllers;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenAPISpecFunctionalTest {
    private static final String SPEC_ENDPOINT = "/v3/api-docs/pre-api";

    @LocalServerPort
    private int port;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = String.format("http://localhost:%s", port);
    }

    @Test
    void functionalTest() {
        final var response = given()
            .relaxedHTTPSValidation()
            .when()
            .get(SPEC_ENDPOINT)
            .thenReturn();

        assertThat(response.statusCode()).isEqualTo(OK.value());
    }
}
