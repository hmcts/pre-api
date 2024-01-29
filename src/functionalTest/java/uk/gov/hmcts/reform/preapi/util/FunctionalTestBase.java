package uk.gov.hmcts.reform.preapi.util;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.preapi.Application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

@SpringBootTest(classes = {Application.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@SuppressWarnings("PMD.JUnit5TestShouldBePackagePrivate")
public class FunctionalTestBase {
    protected static final String CONTENT_TYPE_VALUE = "application/json";

    protected static final String X_USER_ID_HEADER = "X-User-Id";

    protected static UUID authenticatedUserId;

    @Value("${TEST_URL:http://localhost:4550}")
    private String testUrl;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = testUrl;
        if (authenticatedUserId == null) {
            authenticatedUserId = doPostRequest("testing-support/create-authenticated-user", false)
                .body()
                .jsonPath()
                .getUUID("userId");
        }
    }

    protected Response doGetRequest(final String path, final boolean isAuthenticated) {
        return doGetRequest(path, null, isAuthenticated);
    }

    protected Response doGetRequest(
        final String path,
        final Map<String, String> additionalHeaders,
        final boolean isAuthenticated
    ) {
        Logger.getAnonymousLogger().info("GET " + path);
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, isAuthenticated))
            .when()
            .get(path)
            .thenReturn();
    }

    protected Response doPutRequest(final String path, final String body, final boolean isAuthenticated) {
        return doPutRequest(path, null, body, isAuthenticated);
    }

    protected Response doPutRequest(
        final String path,
        final Map<String, String> additionalHeaders,
        final String body,
        final boolean isAuthenticated
    ) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, isAuthenticated))
            .body(body)
            .when()
            .put(path)
            .thenReturn();
    }

    protected Response doPostRequest(final String path, final boolean isAuthenticated) {
        return doPostRequest(path, null, "", isAuthenticated);
    }

    protected Response doPostRequest(final String path, final String body, final boolean isAuthenticated) {
        return doPostRequest(path, null, body, isAuthenticated);
    }

    protected Response doPostRequest(final String path,
                                     final Map<String, String> additionalHeaders,
                                     final String body,
                                     final boolean isAuthenticated) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, isAuthenticated))
            .body(body)
            .when()
            .post(path)
            .thenReturn();
    }

    protected Response doDeleteRequest(final String path, final boolean isAuthenticated) {
        return doDeleteRequest(path, null, isAuthenticated);
    }

    protected Response doDeleteRequest(
        final String path,
        final Map<String, String> additionalHeaders,
        final boolean isAuthenticated
    ) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, isAuthenticated))
            .when()
            .delete(path)
            .thenReturn();
    }

    private static Map<String, String> getRequestHeaders(
        final Map<String, String> additionalHeaders,
        final boolean isAuthenticated
    ) {
        final Map<String, String> headers = new ConcurrentHashMap<>(
            Map.of(CONTENT_TYPE, CONTENT_TYPE_VALUE)
        );

        if (isAuthenticated) {
            headers.put(X_USER_ID_HEADER, authenticatedUserId.toString());
        }

        if (!CollectionUtils.isEmpty(additionalHeaders)) {
            headers.putAll(additionalHeaders);
        }
        return headers;
    }
}
