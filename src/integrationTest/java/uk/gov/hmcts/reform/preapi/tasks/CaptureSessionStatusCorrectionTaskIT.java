package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.storage.blob.BlobContainerClient;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.AzuriteHelperUtil;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
public class CaptureSessionStatusCorrectionTaskIT extends IntegrationTestBase {

    @Autowired
    private CaptureSessionStatusCorrectionTask captureSessionStatusCorrectionTask;

    @Value("${cron-user-email}") String cronUserEmail;

    @DynamicPropertySource
    static void overrideAzureEndpoint(DynamicPropertyRegistry registry) {
        String endpoint = String.format(
            "http://%s:%d/devstoreaccount1",
            AzuriteHelperUtil.DOCKER_COMPOSE_CONTAINER.getHost(),
            AzuriteHelperUtil.DOCKER_COMPOSE_CONTAINER.getMappedPort(AzuriteHelperUtil.CONTAINER_PORT)
        );
        registry.add("azure.blob.endpointFormat", () -> endpoint);
    }

    public BlobContainerClient createContainer(String containerName) {
        BlobContainerClient testContainer = AzuriteHelperUtil.BLOB_SERVICE_CLIENT.getBlobContainerClient(containerName);
        testContainer.create();
        return testContainer;
    }

    public void deleteContainer(BlobContainerClient containerClient) {
        containerClient.delete();
    }

    @BeforeAll
    public static void setup() {
        AzuriteHelperUtil.initialize();
    }

    @AfterAll
    public static void teardown() {
        AzuriteHelperUtil.stopDocker();
    }

    @Test
    @Transactional
    public void shouldRunWithoutErrorsWhenNoCaptureSessionsExist() {

        Court court = persistCourt();

        //Add the robot user that runs the task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Run correction task
        captureSessionStatusCorrectionTask.run();
    }

    @Test
    @Transactional
    public void shouldNotCorrectStatusOfNonFailedCaptureSessions() {

        //Setup capture session data in database
        Court court = persistCourt();
        Case aCase = persistCase(court, null, CaseState.OPEN, null);
        Booking booking = persistBooking(aCase, Timestamp.from(Instant.now()), null);

        CaptureSession captureSession1 = persistCaptureSession(
            booking,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.RECORDING_AVAILABLE
        );

        //Add the robot user that runs the task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Create containers and add section file blobs
        BlobContainerClient testContainer1 = createBlob(
            captureSession1.getBooking().getId(),
            "0/not_a_section",
            "I_AM_NOT_A_SECTION_FILE"
        );

        //Verify statuses have not changed
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        //Clean up
        deleteContainer(testContainer1);
    }

    @Test
    @Transactional
    public void shouldNotChangeStatusOfFailedCaptureSessionsThatHaveSectionFiles() {

        //Setup failed capture session data in database
        Court court = persistCourt();
        Case aCase = persistCase(court, null, CaseState.OPEN, null);
        Booking booking1 = persistBooking(aCase, Timestamp.from(Instant.now()), null);
        Booking booking2 = persistBooking(aCase, Timestamp.from(Instant.now()), null);

        CaptureSession captureSession1 = persistCaptureSession(
            booking1,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession2 = persistCaptureSession(
            booking2,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            RecordingStatus.FAILURE
        );

        //Add the robot user that runs the task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Create containers and add section file blobs
        BlobContainerClient testContainer1 = createBlob(
            captureSession1.getBooking().getId(),
            "0/section",
            "I_AM_A_SECTION_FILE"
        );
        BlobContainerClient testContainer2 = createBlob(
            captureSession2.getBooking().getId(),
            "0/section",
            "I_AM_A_SECTION_FILE"
        );

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have not changed
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.FAILURE);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        //Clean up
        deleteContainer(testContainer1);
        deleteContainer(testContainer2);
    }

    @Test
    @Transactional
    public void shouldCorrectTheStatusOfUnusedCaptureSessions() {

        //Setup unused capture session data in database
        Court court = persistCourt();
        Case aCase = persistCase(court, null, CaseState.OPEN, null);
        Booking booking1 = persistBooking(aCase, Timestamp.from(Instant.now()), null);
        Booking booking2 = persistBooking(aCase, Timestamp.from(Instant.now()), null);

        CaptureSession captureSession1 = persistCaptureSession(
            booking1,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession2 = persistCaptureSession(
            booking2,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            RecordingStatus.FAILURE
        );

        //Add the robot user that runs the task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Create containers and add non-section file blobs
        BlobContainerClient testContainer =
            createBlob(captureSession1.getBooking().getId(), "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");
        BlobContainerClient testContainer2 =
            createBlob(captureSession2.getBooking().getId(), "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have been corrected
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);

        //Clean up
        deleteContainer(testContainer);
        deleteContainer(testContainer2);
    }

    @Test
    @Transactional
    public void shouldUpdateStatusOfFailedCaptureSessionsWithoutContainersToNoRecording() {

        //Setup unused capture session data in database
        Court court = persistCourt();
        Case aCase = persistCase(court, null, CaseState.OPEN, null);
        Booking booking1 = persistBooking(aCase, Timestamp.from(Instant.now()), null);
        Booking booking2 = persistBooking(aCase, Timestamp.from(Instant.now()), null);

        CaptureSession captureSession1 = persistCaptureSession(
            booking1,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession2 = persistCaptureSession(
            booking2,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            RecordingStatus.FAILURE
        );

        //Add the robot user that runs the task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have been corrected
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.NO_RECORDING);
    }

    @Test
    @Transactional
    public void shouldNotCorrectCaptureSessionsThatAreFromPendingClosureCases() {

        //Setup unused capture session data in database
        Court court = persistCourt();
        Case pendingClosureCase = persistCase(court, null, CaseState.PENDING_CLOSURE, null);
        Case pendingClosureCase2 = persistCase(court, null, CaseState.PENDING_CLOSURE,
                                              Timestamp.valueOf("2025-09-28 00:00:00"));

        Booking booking1 = persistBooking(pendingClosureCase, Timestamp.from(Instant.now()), null);
        Booking booking2 = persistBooking(pendingClosureCase2, Timestamp.from(Instant.now()), null);

        CaptureSession captureSession1 = persistCaptureSession(
            booking1,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession2 = persistCaptureSession(
            booking2,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            RecordingStatus.FAILURE
        );

        //Add the robot user that signs in to the database
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Create containers and add non-section file blobs
        BlobContainerClient testContainer =
            createBlob(captureSession1.getBooking().getId(),
                       "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");
        BlobContainerClient testContainer2 =
            createBlob(captureSession2.getBooking().getId(),
                       "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have not been corrected
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.FAILURE);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        //Clean up
        deleteContainer(testContainer);
        deleteContainer(testContainer2);
    }

    @Test
    @Transactional
    public void shouldNotCorrectCaptureSessionsThatAreFromClosedCases() {

        //Setup unused capture session data in database
        Court court = persistCourt();
        Case closedCase = persistCase(court, null, CaseState.CLOSED, null);
        closedCase.setState(CaseState.CLOSED);
        entityManager.persist(closedCase);

        Booking booking1 = persistBooking(closedCase, Timestamp.from(Instant.now()), null);
        Booking booking2 = persistBooking(closedCase, Timestamp.from(Instant.now()), null);

        CaptureSession captureSession1 = persistCaptureSession(
            booking1,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession2 = persistCaptureSession(
            booking2,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            RecordingStatus.FAILURE
        );

        //Add the robot user that signs in to the database
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        BlobContainerClient testContainer = createBlob(captureSession1.getBooking().getId(),
                                                       "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");
        BlobContainerClient testContainer2 = createBlob(captureSession2.getBooking().getId(),
                                                        "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have not been corrected
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.FAILURE);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.FAILURE);

        //Clean up
        deleteContainer(testContainer);
        deleteContainer(testContainer2);
    }

    @Test
    @Transactional
    public void shouldUpdateUnusedCaptureSessionAssociatedWithDeletedBookingsGracefully() {

        //Setup unused capture session data in database
        Court court = persistCourt();
        Case aCase = persistCase(court, null, CaseState.OPEN, null);
        entityManager.persist(aCase);

        Booking deletedBooking = persistBooking(aCase, Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()));
        Booking booking = persistBooking(aCase, Timestamp.from(Instant.now()), null);
        Booking booking2 = persistBooking(aCase, Timestamp.from(Instant.now()), null);
        Booking deletedBooking2 = persistBooking(aCase, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        CaptureSession captureSessionFromDeletedBooking = persistCaptureSession(deletedBooking,
            null,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession1 = persistCaptureSession(booking,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSession2 = persistCaptureSession(booking2,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            RecordingStatus.FAILURE
        );

        CaptureSession captureSessionFromDeletedBooking2 = persistCaptureSession(deletedBooking2,
            null,
            Timestamp.valueOf("2025-12-15 12:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        //Add the robot user that runs task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        //Create containers and add non-section file blobs
        BlobContainerClient testContainer =
            createBlob(captureSession1.getBooking().getId(),
                       "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");
        BlobContainerClient testContainer2
            = createBlob(captureSession2.getBooking().getId(),
                         "I_AM_NOT_A_SECTION_FILE", "I_AM_NOT_A_SECTION_FILE");
        BlobContainerClient testContainer3 = createBlob(
            captureSessionFromDeletedBooking.getBooking().getId(),
            "I_AM_NOT_A_SECTION_FILE",
            "I_AM_NOT_A_SECTION_FILE"
        );
        BlobContainerClient testContainer4 = createBlob(
            captureSessionFromDeletedBooking2.getBooking().getId(),
            "I_AM_NOT_A_SECTION_FILE",
            "I_AM_NOT_A_SECTION_FILE"
        );

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have been corrected
        captureSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        captureSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());
        captureSessionFromDeletedBooking = entityManager.find(
            CaptureSession.class, captureSessionFromDeletedBooking.getId());
        captureSessionFromDeletedBooking2 = entityManager.find(
            CaptureSession.class, captureSessionFromDeletedBooking2.getId());

        assertThat(captureSession1)
            .extracting("id", "status")
            .containsExactlyInAnyOrder(captureSession1.getId(), RecordingStatus.NO_RECORDING);

        assertThat(captureSession2)
            .extracting("id", "status")
            .containsExactlyInAnyOrder(captureSession2.getId(), RecordingStatus.NO_RECORDING);

        assertThat(captureSessionFromDeletedBooking)
            .extracting("id", "status")
            .containsExactlyInAnyOrder(captureSessionFromDeletedBooking.getId(), RecordingStatus.NO_RECORDING);

        assertThat(captureSessionFromDeletedBooking2)
            .extracting("id", "status")
            .containsExactlyInAnyOrder(captureSessionFromDeletedBooking2.getId(), RecordingStatus.NO_RECORDING);

        //Clean up
        deleteContainer(testContainer);
        deleteContainer(testContainer2);
        deleteContainer(testContainer3);
        deleteContainer(testContainer4);
    }

    @Test
    @Transactional
    public void shouldUpdateUnusedCaptureSessionAssociatedWithDeletedUsersGracefully() {

        //Setup unused capture session data in database
        Court court = persistCourt();
        Case aCase = persistCase(court, null, CaseState.OPEN, null);
        User deletedUser = persistUser("Iamdeleted@deleted.com", Timestamp.from(Instant.now()));
        Booking booking = persistBooking(aCase, Timestamp.from(Instant.now()), null);

        CaptureSession captureSessionFromDeletedUser = persistCaptureSession(
            booking,
            deletedUser,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            null,
            RecordingStatus.FAILURE
        );

        // Add the robot user that runs the task
        persistRobotUser(cronUserEmail, court);

        entityManager.flush();

        // Create blob container and add non-section file blob
        BlobContainerClient testContainer = createBlob(
            captureSessionFromDeletedUser.getBooking().getId(),
            "I_AM_NOT_A_SECTION_FILE",
            "I_AM_NOT_A_SECTION_FILE"
        );

        //Run correction task
        captureSessionStatusCorrectionTask.run();
        entityManager.flush();
        entityManager.clear();

        //Verify statuses of capture sessions from deleted bookings have not been corrected
        captureSessionFromDeletedUser = entityManager.find(CaptureSession.class, captureSessionFromDeletedUser.getId());

        assertThat(captureSessionFromDeletedUser)
            .extracting("id", "status")
            .containsExactlyInAnyOrder(captureSessionFromDeletedUser.getId(), RecordingStatus.NO_RECORDING);

        //Clean up
        deleteContainer(testContainer);
    }

    private Booking persistBooking(Case aCase, Timestamp scheduledFor, Timestamp deletedAt) {
        Booking booking = HelperFactory.createBooking(aCase, scheduledFor, deletedAt, null);
        entityManager.persist(booking);
        return booking;
    }

    private CaptureSession persistCaptureSession(
        Booking booking,
        User startedBy,
        Timestamp startedAt,
        Timestamp deletedAt,
        RecordingStatus status
    ) {
        CaptureSession captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            startedAt,
            startedBy,
            null,
            null,
            status,
            deletedAt
        );
        entityManager.persist(captureSession);
        return captureSession;
    }

    private Court persistCourt() {
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Default Court", "0000");
        entityManager.persist(court);
        return court;
    }

    private Case persistCase(Court court, Timestamp deletedAt, CaseState state, Timestamp closedAt) {
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, deletedAt);
        aCase.setState(state);
        aCase.setClosedAt(closedAt);
        entityManager.persist(aCase);
        return aCase;
    }

    private User persistUser(String email, Timestamp deletedAt) {
        User user = HelperFactory.createUser("first", "last", email, deletedAt, null, null);
        entityManager.persist(user);
        return user;
    }

    private User persistRobotUser(String email, Court court) {
        User robotUser = persistUser(email, null);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        return robotUser;
    }

    private BlobContainerClient createBlob(UUID bookingId, String blobName, String content) {
        BlobContainerClient container = createContainer(String.valueOf(bookingId));
        container.getBlobClient(blobName).upload(
            new ByteArrayInputStream(content.getBytes()),
            content.length()
        );
        return container;
    }
}
