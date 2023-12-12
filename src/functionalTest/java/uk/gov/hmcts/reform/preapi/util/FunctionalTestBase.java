package uk.gov.hmcts.reform.preapi.util;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.preapi.Application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

@SpringBootTest(classes = {Application.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@SuppressWarnings("PMD.JUnit5TestShouldBePackagePrivate")
public class FunctionalTestBase {
    protected static final String CONTENT_TYPE_VALUE = "application/json";

    @Value("${TEST_URL:http://localhost:4550}")
    private String testUrl;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = testUrl;
    }

    protected Response doGetRequest(final String path) {
        return doGetRequest(path, null);
    }

    protected Response doGetRequest(final String path, final Map<String, String> additionalHeaders) {
        Logger.getAnonymousLogger().info("GET " + path);
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders))
            .when()
            .get(path)
            .thenReturn();
    }

    protected Response doPutRequest(final String path, final String body) {
        return doPutRequest(path, null, body);
    }

    protected Response doPutRequest(final String path, final Map<String, String> additionalHeaders, final String body) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders))
            .body(body)
            .when()
            .put(path)
            .thenReturn();
    }

    protected Response doPostRequest(final String path, final String body) {
        return doPostRequest(path, null, body);
    }

    protected Response doPostRequest(final String path, final Map<String, String> additionalHeaders, final String body) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders))
            .body(body)
            .when()
            .post(path)
            .thenReturn();
    }

    protected Response doDeleteRequest(final String path) {
        return doDeleteRequest(path, null);
    }

    protected Response doDeleteRequest(final String path, final Map<String, String> additionalHeaders) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders))
            .when()
            .delete(path)
            .thenReturn();
    }

    private static Map<String, String> getRequestHeaders(final Map<String, String> additionalHeaders) {
        final Map<String, String> headers = new ConcurrentHashMap<>(Map.of(CONTENT_TYPE, CONTENT_TYPE_VALUE));
        if (!CollectionUtils.isEmpty(additionalHeaders)) {
            headers.putAll(additionalHeaders);
        }
        return headers;
    }
}
