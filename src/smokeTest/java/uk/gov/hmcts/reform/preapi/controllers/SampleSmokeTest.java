package uk.gov.hmcts.reform.preapi.controllers;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.logging.Logger;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class SampleSmokeTest {
    protected static final String CONTENT_TYPE_VALUE = "application/json";

    @Value("${TEST_URL:http://localhost:8080}")
    private String testUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.host}")
    private String dbHost;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = testUrl;
        RestAssured.useRelaxedHTTPSValidation();
    }

    @Test
    void smokeTest() {
        Logger.getGlobal().info("dbUser: " + dbUser);
        Logger.getGlobal().info("dbHost: " + dbHost);
        Response response = given()
            .contentType(ContentType.JSON)
            .when()
            .get()
            .then()
            .extract().response();

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.asString().startsWith("Welcome"));
    }
}
