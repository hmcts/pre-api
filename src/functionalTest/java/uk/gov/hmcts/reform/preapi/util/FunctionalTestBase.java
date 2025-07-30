package uk.gov.hmcts.reform.preapi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@SpringBootTest(classes = { Application.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    protected static final String LEGACY_REPORTS_ENDPOINT = "/reports";
    protected static final String REPORTS_ENDPOINT = "/reports-v2";

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static Map<TestingSupportRoles, AuthUserDetails> authenticatedUserIds;

    @LocalServerPort
    private int port;

    public String testUrl = "";

    @BeforeAll
    static void beforeAll() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

    private static Map<String, String> getRequestHeaders(
        final Map<String, String> additionalHeaders,
        final TestingSupportRoles authenticatedAs
    ) {
        final Map<String, String> headers = new ConcurrentHashMap<>(
            Map.of(CONTENT_TYPE, CONTENT_TYPE_VALUE)
        );

        if (authenticatedAs != null && authenticatedUserIds.get(authenticatedAs) != null) {
            headers.put(X_USER_ID_HEADER, authenticatedUserIds.get(authenticatedAs).accessId().toString());
        }

        if (!CollectionUtils.isEmpty(additionalHeaders)) {
            headers.putAll(additionalHeaders);
        }
        return headers;
    }

    protected static void assertResponseCode(Response response, int expectedStatusCode) {
        assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
    }

    @BeforeEach
    void setUp() {
        testUrl = String.format("http://localhost:%s", port);
        RestAssured.baseURI = testUrl;

        if (authenticatedUserIds == null) {
            authenticatedUserIds = new HashMap<>();
            Arrays.stream(TestingSupportRoles.values())
                .forEach(role -> authenticatedUserIds.put(
                    role,
                    doPostRequest("/testing-support/create-authenticated-user/" + role, null)
                        .body()
                        .jsonPath()
                        .getObject("", AuthUserDetails.class)
                ));
        }
    }

    protected Response doGetRequest(final String path, final TestingSupportRoles authenticatedAs) {
        return doGetRequest(path, null, authenticatedAs);
    }

    protected Response doGetRequest(
        final String path,
        final Map<String, String> additionalHeaders,
        final TestingSupportRoles authenticatedAs
    ) {
        Logger.getAnonymousLogger().info("GET " + path);
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, authenticatedAs))
            .when()
            .get(path)
            .thenReturn();
    }

    protected Response doPutRequest(final String path, final String body, final TestingSupportRoles authenticatedAs) {
        return doPutRequest(path, null, body, authenticatedAs);
    }

    protected Response doPutRequest(
        final String path,
        final Map<String, String> additionalHeaders,
        final String body,
        final TestingSupportRoles authenticatedAs
    ) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, authenticatedAs))
            .body(body)
            .when()
            .put(path)
            .thenReturn();
    }

    protected Response doPostRequest(final String path, final TestingSupportRoles authenticatedAs) {
        return doPostRequest(path, null, "", authenticatedAs);
    }

    protected Response doPostRequest(final String path, final String body, final TestingSupportRoles authenticatedAs) {
        return doPostRequest(path, null, body, authenticatedAs);
    }

    protected Response doPostRequest(final String path,
                                     final Map<String, String> additionalHeaders,
                                     final String body,
                                     final TestingSupportRoles authenticatedAs) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, authenticatedAs))
            .body(body)
            .when()
            .post(path)
            .thenReturn();
    }

    protected Response doPostRequestWithMultipart(final String path,
                                             final Map<String, String> additionalHeaders,
                                             final String filePath,
                                             final TestingSupportRoles authenticatedAs) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, authenticatedAs))
            .multiPart("file", new File(filePath), "text/csv")
            .when()
            .post(path)
            .thenReturn();
    }

    protected Response doDeleteRequest(final String path, final TestingSupportRoles authenticatedAs) {
        return doDeleteRequest(path, null, authenticatedAs);
    }

    protected Response doDeleteRequest(
        final String path,
        final Map<String, String> additionalHeaders,
        final TestingSupportRoles authenticatedAs
    ) {
        return given()
            .relaxedHTTPSValidation()
            .headers(getRequestHeaders(additionalHeaders, authenticatedAs))
            .when()
            .delete(path)
            .thenReturn();
    }

    protected CreateCaseDTO createCase() {
        var courtId = UUID.fromString(doPostRequest("/testing-support/create-court", null)
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
        caseDTO.setState(CaseState.OPEN);

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
        var regionId = doPostRequest("/testing-support/create-region", null)
            .body().jsonPath().getUUID("regionId");

        var dto = new CreateCourtDTO();
        dto.setId(UUID.randomUUID());
        dto.setName("Example Court");
        dto.setCourtType(CourtType.CROWN);
        dto.setRooms(List.of(UUID.randomUUID()));
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

    protected Response assertExists(String endpoint, UUID id, boolean shouldExist) {
        var response = doGetRequest(endpoint + "/" + id, TestingSupportRoles.SUPER_USER);
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
        var response = doGetRequest(INVITES_ENDPOINT + "/" + userId, TestingSupportRoles.SUPER_USER);
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
        return doPutRequest(
            USERS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected Response putUser(CreateUserDTO dto, TestingSupportRoles authenticatedAs) throws JsonProcessingException {
        return doPutRequest(
            USERS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            authenticatedAs
        );
    }

    protected Response putCourt(CreateCourtDTO dto) throws JsonProcessingException {
        return doPutRequest(
            COURTS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected Response putCase(CreateCaseDTO dto) throws JsonProcessingException {
        return doPutRequest(
            CASES_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected Response putCaptureSession(CreateCaptureSessionDTO dto) throws JsonProcessingException {
        return doPutRequest(
            CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected CreateParticipantDTO convertDtoToCreateDto(ParticipantDTO dto) {
        var create = new CreateParticipantDTO();
        create.setId(dto.getId());
        create.setParticipantType(dto.getParticipantType());
        create.setFirstName(dto.getFirstName());
        create.setLastName(dto.getLastName());
        return create;
    }

    protected CreateCaseDTO convertDtoToCreateDto(CaseDTO dto) {
        var create = new CreateCaseDTO();
        create.setId(dto.getId());
        create.setCourtId(dto.getCourt().getId());
        create.setReference(dto.getReference());
        create.setTest(dto.isTest());
        create.setState(dto.getState());
        create.setClosedAt(dto.getClosedAt());
        create.setParticipants(dto.getParticipants()
                                   .stream()
                                   .map(this::convertDtoToCreateDto)
                                   .collect(Collectors.toSet()));
        return create;
    }

    protected CreateBookingDTO createBooking(UUID caseId, Set<CreateParticipantDTO> participants) {
        var dto = new CreateBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaseId(caseId);
        dto.setParticipants(participants);
        dto.setScheduledFor(Timestamp.valueOf(LocalDate.now().atStartOfDay()));
        return dto;
    }

    protected Response putBooking(CreateBookingDTO dto) throws JsonProcessingException {
        return doPutRequest(
            BOOKINGS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected CreateUserDTO createUser(String firstName) {
        var dto = new CreateUserDTO();
        dto.setId(UUID.randomUUID());
        dto.setFirstName(firstName);
        dto.setLastName("Example");
        dto.setAppAccess(Set.of());
        dto.setPortalAccess(Set.of());
        dto.setEmail(dto.getId() + "@example.com");
        return dto;
    }

    protected CreateShareBookingDTO createShareBooking(UUID bookingId, UUID shareWithId) {
        var dto = new CreateShareBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setSharedWithUser(shareWithId);
        return dto;
    }

    protected CreateCaptureSessionDTO createCaptureSession(UUID bookingId) {
        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setOrigin(RecordingOrigin.PRE);
        return dto;
    }

    protected CreateCaptureSessionDTO createCaptureSession() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath().getUUID("bookingId");

        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setOrigin(RecordingOrigin.PRE);
        return dto;
    }

    protected CreateRecordingDTO createRecording(UUID captureSessionId) {
        var dto = new CreateRecordingDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaptureSessionId(captureSessionId);
        dto.setEditInstructions("{}");
        dto.setVersion(1);
        dto.setUrl("example url");
        dto.setFilename("example.file");
        return dto;
    }

    protected CreateRecordingResponse createRecording() {
        var response = doPostRequest("/testing-support/should-delete-recordings-for-booking", null);
        assertResponseCode(response, 200);
        return response.body().jsonPath().getObject("", CreateRecordingResponse.class);
    }

    protected CreateCaptureSessionDTO setupCaptureSessionWithOrigins(RecordingOrigin caseOrigin,
                                                                     RecordingOrigin captureSessionOrigin,
                                                                     UUID courtId) throws JsonProcessingException {
        CreateCaseDTO dto = createCase();
        dto.setOrigin(caseOrigin);
        dto.setCourtId(courtId);
        Response putCase = putCase(dto);
        assertResponseCode(putCase, 201);

        CreateBookingDTO bookingDTO = createBooking(dto.getId(), dto.getParticipants());
        Response putBooking = putBooking(bookingDTO);
        assertResponseCode(putBooking, 201);

        CreateCaptureSessionDTO createCaptureSessionDTO = createCaptureSession(bookingDTO.getId());
        createCaptureSessionDTO.setOrigin(captureSessionOrigin);
        return createCaptureSessionDTO;
    }

    protected Response putShareBooking(CreateShareBookingDTO dto) throws JsonProcessingException {
        return doPutRequest(
            BOOKINGS_ENDPOINT + "/" + dto.getBookingId() + "/share",
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected Response putRecording(CreateRecordingDTO dto) throws JsonProcessingException {
        return doPutRequest(
            RECORDINGS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    protected record AuthUserDetails(UUID accessId, UUID courtId) {
    }

    protected record CreateRecordingResponse(UUID caseId, UUID bookingId, UUID captureSessionId, UUID recordingId) {
    }
}
