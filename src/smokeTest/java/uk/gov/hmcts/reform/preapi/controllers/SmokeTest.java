package uk.gov.hmcts.reform.preapi.controllers;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;

@ExtendWith(SpringExtension.class)
class SmokeTest {
    private static final String CONTENT_TYPE_VALUE = "application/json";
    private static final String HEALTH_ENDPOINT = "/health";

    @Value("${TEST_URL:http://localhost:4545}")
    private String testUrl;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = testUrl;
    }

    @Test
    void healthCheck() {
        final var response = given()
            .relaxedHTTPSValidation()
            .headers(CONTENT_TYPE, CONTENT_TYPE_VALUE)
            .when()
            .get(HEALTH_ENDPOINT)
            .thenReturn();

        assertThat(response.statusCode()).isEqualTo(OK.value());
        assertThat(response.asString().substring(1)).startsWith("\"status\":\"UP\"");
    }
}
