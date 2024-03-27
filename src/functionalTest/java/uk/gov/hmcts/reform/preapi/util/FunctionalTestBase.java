package uk.gov.hmcts.reform.preapi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@SuppressWarnings("PMD.JUnit5TestShouldBePackagePrivate")
public class FunctionalTestBase {
    protected static final String CONTENT_TYPE_VALUE = "application/json";
    protected static final String CASES_ENDPOINT = "/cases";
    protected static final String BOOKINGS_ENDPOINT = "/bookings";
    protected static final String CAPTURE_SESSIONS_ENDPOINT = "/capture-sessions";
    protected static final String RECORDINGS_ENDPOINT = "/recordings";
    protected static final String AUDIT_ENDPOINT = "/audit/";

    protected static UUID authenticatedUserId;
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @Value("${TEST_URL:http://localhost:4550}")
    private String testUrl;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = testUrl;
        if (authenticatedUserId == null) {
            authenticatedUserId = doPostRequest("/testing-support/create-authenticated-user/super-user", false)
                .body()
                .jsonPath()
                .getUUID("accessId");
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

    protected CreateCaseDTO createCase() {
        var courtId = UUID.fromString(doPostRequest("/testing-support/create-court", false)
                                          .body().jsonPath().get("courtId"));

        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(UUID.randomUUID());
        caseDTO.setCourtId(courtId);
        caseDTO.setReference(generateRandomCaseReference());
        caseDTO.setParticipants(Set.of());
        caseDTO.setTest(false);

        return caseDTO;
    }

    protected String generateRandomCaseReference() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 13);
    }

    protected void assertResponse401(Response response) {
        assertThat(response.statusCode()).isEqualTo(401);
    }
}
