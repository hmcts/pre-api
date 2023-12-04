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
class CaseControllerGetAllFunctionalTest {
    private static final String CASES_PATH = "/cases";
    private static final String VALID_CASE_ID = "d44b6109-65d2-46a7-ab94-bee374f8b780";
    private static final String CASE_REFERENCE = "CASE123";
    private static final String VALID_COURT_ID = "7983a646-7168-43cf-81fc-14d5c35297c2";
    private static final String FIND_ALL_FIRST_ID = "[0].id";
    private static final String FIND_ALL_FIRST_REFERENCE = "[0].reference";
    private static final String FIND_ALL_FIRST_COURT_ID = "[0].court_id";
    private static final String FIND_ALL_FIRST_TEST = "[0].test";

    private static final String PARAM_REFERENCE = "reference";

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
    void findAllNoSearchParamsSuccess() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH)
            .get()
            .then()
            .extract().response();

        Assertions.assertEquals(200, res.statusCode());
        // Size of 1 means it has ignored the deleted cases
        Assertions.assertEquals(1, res.jsonPath().getList("").size());
        Assertions.assertEquals(CASE_REFERENCE, res.getBody().jsonPath().get(FIND_ALL_FIRST_REFERENCE));
        Assertions.assertEquals(false, res.getBody().jsonPath().get(FIND_ALL_FIRST_TEST));
        Assertions.assertEquals(VALID_CASE_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_ID));
        Assertions.assertEquals(VALID_COURT_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_COURT_ID));
    }

    @Test
    void findAllWithSearchParamsSuccess() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH)
            .param(PARAM_REFERENCE, "case")
            .param("courtId", VALID_COURT_ID)
            .get()
            .then()
            .extract().response();


        Assertions.assertEquals(200, res.statusCode());
        Assertions.assertEquals(1, res.jsonPath().getList("").size());
        Assertions.assertEquals(CASE_REFERENCE, res.getBody().jsonPath().get(FIND_ALL_FIRST_REFERENCE));
        Assertions.assertEquals(false, res.getBody().jsonPath().get(FIND_ALL_FIRST_TEST));
        Assertions.assertEquals(VALID_CASE_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_ID));
        Assertions.assertEquals(VALID_COURT_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_COURT_ID));
    }

    @Test
    void findAllWithSearchParamReferenceSuccess() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH)
            .param(PARAM_REFERENCE, "case")
            .get()
            .then()
            .extract().response();

        Assertions.assertEquals(200, res.statusCode());
        Assertions.assertEquals(1, res.jsonPath().getList("").size());
        Assertions.assertEquals(CASE_REFERENCE, res.getBody().jsonPath().get(FIND_ALL_FIRST_REFERENCE));
        Assertions.assertEquals(false, res.getBody().jsonPath().get(FIND_ALL_FIRST_TEST));
        Assertions.assertEquals(VALID_CASE_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_ID));
        Assertions.assertEquals(VALID_COURT_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_COURT_ID));
    }

    @Test
    void findAllWithSearchParamCourtSuccess() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH)
            .param("courtId", "7983a646-7168-43cf-81fc-14d5c35297c2")
            .get()
            .then()
            .extract().response();


        Assertions.assertEquals(200, res.statusCode());
        Assertions.assertEquals(1, res.jsonPath().getList("").size());
        Assertions.assertEquals(CASE_REFERENCE, res.getBody().jsonPath().get(FIND_ALL_FIRST_REFERENCE));
        Assertions.assertEquals(false, res.getBody().jsonPath().get(FIND_ALL_FIRST_TEST));
        Assertions.assertEquals(VALID_CASE_ID, res.getBody().jsonPath().get(FIND_ALL_FIRST_ID));
        Assertions.assertEquals(VALID_COURT_ID,  res.getBody().jsonPath().get(FIND_ALL_FIRST_COURT_ID));
    }

    @Test
    void findAllWithSearchParamsNoDataSuccess() {
        Response res = given()
            .contentType(ContentType.JSON)
            .when()
            .baseUri(testUrl + CASES_PATH)
            .param("reference", "RandomReference")
            .get()
            .then()
            .extract().response();

        Assertions.assertEquals(200, res.statusCode());
        Assertions.assertEquals(0, res.jsonPath().getList("").size());

    }
}
