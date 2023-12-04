package uk.gov.hmcts.reform.preapi.cases.controllers;


import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import uk.gov.hmcts.reform.preapi.helpers.DatabaseHelper;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Sql("classpath:/db/local-test-data/test-data.sql")
class CaseControllerDeleteFunctionalTest {
    private static final String CASES_PATH = "/cases";

    private static final String INVALID_CASE_ID = "abc";
    private static final String NOT_FOUND_CASE_ID = "d44b6109-65d2-46a7-ab94-bee374f8b789";
    private static final String VALID_CASE_ID = "d44b6109-65d2-46a7-ab94-bee374f8b780";
    private static final String DELETED_CASE_ID = "c6f3e040-3086-4942-910d-4fe8b38b49ca";

    @Autowired
    private DatabaseHelper databaseHelper;

    @Value("${TEST_URL:http://localhost:4550}")
    private String testUrl;

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = testUrl;
        RestAssured.useRelaxedHTTPSValidation();
    }

    @AfterEach
    void afterEach() {
        databaseHelper.clearDatabase();
    }

    @Test
    void deleteByIdBadRequestTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH + "/" + INVALID_CASE_ID)
            .delete()
            .then()
            .extract().response();

        Assertions.assertEquals(400, res.statusCode());
    }

    @Test
    void deleteByIdNotFoundTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH + "/" + NOT_FOUND_CASE_ID)
            .delete()
            .then()
            .extract().response();

        Assertions.assertEquals(404, res.statusCode());
    }

    @Test
    void deleteByIdDeletedTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH + "/" + DELETED_CASE_ID)
            .delete()
            .then()
            .extract().response();

        Assertions.assertEquals(404, res.statusCode());
    }

    @Test
    void deleteByIdSuccessTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH + "/" + VALID_CASE_ID)
            .delete()
            .then()
            .extract().response();

        Assertions.assertEquals(200, res.statusCode());
    }
}
