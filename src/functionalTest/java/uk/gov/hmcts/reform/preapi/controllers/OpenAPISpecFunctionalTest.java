package uk.gov.hmcts.reform.preapi.controllers;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class OpenAPISpecFunctionalTest {
    private static final String SPEC_ENDPOINT = "/v3/api-docs";

    @Value("${TEST_URL:http://localhost:4550}")
    private String testUrl;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = testUrl;
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
