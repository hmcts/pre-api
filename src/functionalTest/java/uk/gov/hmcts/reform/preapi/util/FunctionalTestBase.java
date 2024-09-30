package uk.gov.hmcts.reform.preapi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.text.SimpleDateFormat;
import java.util.List;
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
    protected static final String COURTS_ENDPOINT = "/courts";
    protected static final String CASES_ENDPOINT = "/cases";
    protected static final String BOOKINGS_ENDPOINT = "/bookings";
    protected static final String CAPTURE_SESSIONS_ENDPOINT = "/capture-sessions";
    protected static final String RECORDINGS_ENDPOINT = "/recordings";
    protected static final String AUDIT_ENDPOINT = "/audit/";
    protected static final String USERS_ENDPOINT = "/users";
    protected static final String INVITES_ENDPOINT = "/invites";
    protected static final String LOCATION_HEADER = "Location";
    protected static final String REPORTS_ENDPOINT = "/reports";
    protected static UUID authenticatedUserId;
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @Value("${TEST_URL:http://localhost:4550}")
    protected String testUrl;

    @BeforeAll
    static void beforeAll() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

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
        caseDTO.setParticipants(Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        ));
        caseDTO.setTest(false);

        return caseDTO;
    }

    protected CreateParticipantDTO createParticipant(ParticipantType type) {
        var dto = new CreateParticipantDTO();
        dto.setId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Person");
        dto.setParticipantType(type);
        return dto;
    }

    protected CreateCourtDTO createCourt() {
        var roomId = doPostRequest("/testing-support/create-room", false).body().jsonPath().getUUID("roomId");
        var regionId = doPostRequest("/testing-support/create-region", false).body().jsonPath().getUUID("regionId");

        var dto = new CreateCourtDTO();
        dto.setId(UUID.randomUUID());
        dto.setName("Example Court");
        dto.setCourtType(CourtType.CROWN);
        dto.setRooms(List.of(roomId));
        dto.setRegions(List.of(regionId));
        dto.setLocationCode("123456789");
        return dto;
    }

    protected String generateRandomCaseReference() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 13);
    }

    protected static void assertResponseCode(Response response, int expectedStatusCode) {
        assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
    }

    protected Response assertExists(String endpoint, UUID id, boolean shouldExist) {
        var response = doGetRequest(endpoint + "/" + id, true);
        assertResponseCode(response, shouldExist ? 200 : 404);
        if (shouldExist) {
            assertThat(response.body().jsonPath().getUUID("id")).isEqualTo(id);
        }
        return response;
    }

    protected Response assertCourtExists(UUID courtId, boolean shouldExist) {
        return assertExists(COURTS_ENDPOINT, courtId, shouldExist);
    }

    protected Response assertCaseExists(UUID caseId, boolean shouldExist) {
        return assertExists(CASES_ENDPOINT, caseId, shouldExist);
    }

    protected Response assertBookingExists(UUID bookingId, boolean shouldExist) {
        return assertExists(BOOKINGS_ENDPOINT, bookingId, shouldExist);
    }

    protected Response assertCaptureSessionExists(UUID captureSessionId, boolean shouldExist) {
        return assertExists(CAPTURE_SESSIONS_ENDPOINT, captureSessionId, shouldExist);
    }

    protected Response assertRecordingExists(UUID recordingId, boolean shouldExist) {
        return assertExists(RECORDINGS_ENDPOINT, recordingId, shouldExist);
    }

    protected Response assertInviteExists(UUID userId, boolean shouldExist) {
        var response = doGetRequest(INVITES_ENDPOINT + "/" + userId, true);
        assertResponseCode(response, shouldExist ? 200 : 404);
        if (shouldExist) {
            assertThat(response.body().jsonPath().getUUID("user_id")).isEqualTo(userId);
        }
        return response;
    }

    protected Response assertUserExists(UUID userId, boolean shouldExist) {
        return assertExists(USERS_ENDPOINT, userId, shouldExist);
    }

    protected Response putUser(CreateUserDTO dto) throws JsonProcessingException {
        return doPutRequest(USERS_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }

    protected Response putCourt(CreateCourtDTO dto) throws JsonProcessingException {
        return doPutRequest(COURTS_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }

    protected Response putCase(CreateCaseDTO dto) throws JsonProcessingException {
        return doPutRequest(CASES_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }

    protected Response putCaptureSession(CreateCaptureSessionDTO dto) throws JsonProcessingException {
        return doPutRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }
}
