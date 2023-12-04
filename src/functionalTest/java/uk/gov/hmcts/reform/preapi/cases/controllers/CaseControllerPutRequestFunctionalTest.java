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

import java.util.UUID;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Sql("classpath:/db/local-test-data/test-data.sql")
class CaseControllerPutRequestFunctionalTest {
    private static final String CASES_PATH = "/cases";

    private static final String INVALID_CASE_ID = "abc";
    private static final String VALID_CASE_ID = "d44b6109-65d2-46a7-ab94-bee374f8b780";
    private static final String VALID_COURT_ID = "47d75f66-a1aa-4deb-b527-e199ecc6cf97";

    private static final String CREATE_CASE_JSON = """
        {
            "id": $ID$,
            "court_id": $COURT_ID$,
            "reference": $REFERENCE$,
            "test": $TEST$
        }
        """;

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
    void createCaseSuccessTest() {
        String uuid = UUID.randomUUID().toString();

        Response res = given()
            .contentType(ContentType.JSON)
            .body(getCreateCaseJson(
                uuid,
                VALID_COURT_ID,
                "createCaseSuccessTest"
            ))
            .when()
            .put(CASES_PATH + "/" + uuid)
            .then()
            .extract().response();

        Assertions.assertEquals(201, res.statusCode());
    }

    @Test
    void createCaseFoundTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .body(getCreateCaseJson(
                VALID_CASE_ID,
                VALID_COURT_ID,
                "createCaseFoundTest"
            ))
            .when()
            .put(CASES_PATH + "/" + VALID_CASE_ID)
            .then()
            .extract().response();

        Assertions.assertEquals(409, res.statusCode());
    }

    @Test
    void createCaseIdMismatchTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .body(getCreateCaseJson(
                UUID.randomUUID().toString(),
                VALID_COURT_ID,
                "createCaseIdMismatchTest"
            ))
            .when()
            .put(CASES_PATH + "/" + VALID_CASE_ID)
            .then()
            .extract().response();

        Assertions.assertEquals(400, res.statusCode());
    }

    @Test
    void createCaseCourtNotFoundTest() {
        Response res = given()
            .contentType(ContentType.JSON)
            .body(getCreateCaseJson(
                VALID_CASE_ID,
                UUID.randomUUID().toString(),
                "createCaseCourtNotFoundTest"
            ))
            .when()
            .put(CASES_PATH + "/" + VALID_CASE_ID)
            .then()
            .extract().response();

        Assertions.assertEquals(400, res.statusCode());
    }

    @Test
    void createCaseBadRequestTest() {
        String body = CREATE_CASE_JSON
            .replace("$COURT_ID$", "\"47d75f66-a1aa-4deb-b527-e199ecc6cf98\"")
            .replace("$REFERENCE$", "\"createCaseBadRequestTest\"")
            .replace("$TEST$", "true");

        Response res = given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put(CASES_PATH + "/" + INVALID_CASE_ID)
            .then()
            .extract().response();

        Assertions.assertEquals(400, res.statusCode());
    }

    private String getCreateCaseJson(String id, String courtId, String reference) {
        return CREATE_CASE_JSON
            .replace("$ID$", '"' + id + '"')
            .replace("$COURT_ID$", '"' + courtId + '"')
            .replace("$REFERENCE$", '"' + reference + '"')
            .replace("$TEST$", "true");
    }
}
