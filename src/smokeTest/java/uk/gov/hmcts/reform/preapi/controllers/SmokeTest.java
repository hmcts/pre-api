package uk.gov.hmcts.reform.preapi.controllers;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

    @Value("${TEST_URL:http://localhost:4550}")
    private String testUrl;

    @Value("${apim.subscription-key.primary}")
    private String primaryApimKey;

    @Value("${apim.subscription-key.secondary}")
    private String secondaryApimKey;

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

    @Test
    @EnabledIfEnvironmentVariable(named = "APIM_ENABLED", matches = "true")
    void apimCheck() {
        var primaryResponse = apimRequest(primaryApimKey);

        if (primaryResponse.statusCode() != OK.value()) {
            var secondaryResponse = apimRequest(secondaryApimKey);
            assertThat(secondaryResponse.statusCode()).isEqualTo(OK.value());
        } else {
            assertThat(primaryResponse.statusCode()).isEqualTo(OK.value());
        }
    }

    private io.restassured.response.Response apimRequest(String key) {
        return given()
            .relaxedHTTPSValidation()
            .headers(CONTENT_TYPE, CONTENT_TYPE_VALUE)
            .headers("Ocp-Apim-Subscription-Key", key)
            .when()
            .get("/courts")
            .andReturn();
    }
}
