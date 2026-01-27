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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

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
    public void shouldNotChangeStatusOfFailedCaptureSessionsThatHaveSectionFiles() {

        //Setup failed capture session data in database and storage
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking1 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking1);
        Booking booking2 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession2);

        //Add the robot user that signs in to the database
        User robotUser = HelperFactory.createUser("robot", "user", cronUserEmail, null, null, null);
        entityManager.persist(robotUser);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        entityManager.flush();

        var testContainer1 = createContainer(String.valueOf(captureSession1.getBooking().getId()));

        var testContainer2 = createContainer(String.valueOf(captureSession2.getBooking().getId()));

        testContainer1.getBlobClient("0/section").upload(
            new ByteArrayInputStream("section file content".getBytes()),
            "section file content".length()
        );

        testContainer2.getBlobClient("0/section").upload(
            new ByteArrayInputStream("section file content".getBytes()),
            "section file content".length()
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

        //Setup unused capture session data in database and storage
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking1 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking1);
        Booking booking2 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession2);

        //Add the robot user that signs in to the database
        User robotUser = HelperFactory.createUser("robot", "user", cronUserEmail, null, null, null);
        entityManager.persist(robotUser);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        entityManager.flush();

        var testContainer1 = createContainer(String.valueOf(captureSession1.getBooking().getId()));

        var testContainer2 = createContainer(String.valueOf(captureSession2.getBooking().getId()));

        testContainer1.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("I_AM_NOT_A_SECTION_FILE".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );

        testContainer2.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("I_AM_NOT_A_SECTION_FILE".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );

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

        //Setup unused capture session data in database and storage that are from a closed and pending close case
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case closedCase = HelperFactory.createCase(court, "CASE12345", true, null);
        closedCase.setClosedAt(Timestamp.from(Instant.now()));
        closedCase.setState(CaseState.CLOSED);
        entityManager.persist(closedCase);

        Case pendingClosureCase = HelperFactory.createCase(court, "CASE12345", true, null);
        pendingClosureCase.setClosedAt(Timestamp.from(Instant.now()));
        pendingClosureCase.setState(CaseState.PENDING_CLOSURE);
        entityManager.persist(pendingClosureCase);

        Booking booking1 = HelperFactory.createBooking(closedCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking1);
        Booking booking2 = HelperFactory.createBooking(pendingClosureCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession2);

        //Add the robot user that signs in to the database
        User robotUser = HelperFactory.createUser("robot", "user", cronUserEmail, null, null, null);
        entityManager.persist(robotUser);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        entityManager.flush();

        var testContainer1 = createContainer(String.valueOf(captureSession1.getBooking().getId()));

        var testContainer2 = createContainer(String.valueOf(captureSession2.getBooking().getId()));

        testContainer1.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("I_AM_NOT_A_SECTION_FILE".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );

        testContainer2.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("I_AM_NOT_A_SECTION_FILE".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have not been corrected
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.FAILURE);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.FAILURE);
    }

    @Test
    @Transactional
    public void shouldNotCorrectCaptureSessionsThatAreFromClosedCases() {

        //Setup unused capture session data in database and storage that are from a closed case
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case closedCase = HelperFactory.createCase(court, "CASE12345", true, null);
        closedCase.setClosedAt(Timestamp.from(Instant.now()));
        closedCase.setState(CaseState.CLOSED);
        entityManager.persist(closedCase);

        Booking booking1 = HelperFactory.createBooking(closedCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking1);
        Booking booking2 = HelperFactory.createBooking(closedCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-09-28 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession2);

        //Add the robot user that signs in to the database
        User robotUser = HelperFactory.createUser("robot", "user", cronUserEmail, null, null, null);
        entityManager.persist(robotUser);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        entityManager.flush();

        var testContainer1 = createContainer(String.valueOf(captureSession1.getBooking().getId()));

        var testContainer2 = createContainer(String.valueOf(captureSession2.getBooking().getId()));

        testContainer1.getBlobClient("gc_state").upload(
            new ByteArrayInputStream("section file content".getBytes()),
            "section file content".length()
        );

        testContainer2.getBlobClient("gc_state").upload(
            new ByteArrayInputStream("gc stuff".getBytes()),
            "gc stuff".length()
        );

        //Run correction task
        captureSessionStatusCorrectionTask.run();

        //Verify statuses have not been corrected
        CaptureSession updatedSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        CaptureSession updatedSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());

        assertThat(updatedSession1.getStatus()).isEqualTo(RecordingStatus.FAILURE);
        assertThat(updatedSession2.getStatus()).isEqualTo(RecordingStatus.FAILURE);
    }

    @Test
    @Transactional
    public void shouldUpdateUnusedCaptureSessionAssociatedWithDeletedBookingsGracefully() {

        //Setup unused capture session data in database and storage
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase.setState(CaseState.OPEN);
        entityManager.persist(aCase);

        Booking deletedBooking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null);
        entityManager.persist(deletedBooking);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);
        Booking booking2 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);
        Booking deletedBooking2 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), null);
        entityManager.persist(deletedBooking2);

        CaptureSession captureSessionFromDeletedBooking = HelperFactory.createCaptureSession(
            deletedBooking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSessionFromDeletedBooking);

        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-03 23:59:59"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession2);

        CaptureSession captureSessionFromDeletedBooking2 = HelperFactory.createCaptureSession(
            deletedBooking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSessionFromDeletedBooking2);

        //Add the robot user that signs in to the database
        User robotUser = HelperFactory.createUser("robot", "user", cronUserEmail, null, null, null);
        entityManager.persist(robotUser);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        entityManager.flush();

        var testContainer1 = createContainer(String.valueOf(captureSession1.getBooking().getId()));
        var testContainer2 = createContainer(String.valueOf(captureSession2.getBooking().getId()));
        var testContainer3 = createContainer(String.valueOf(captureSessionFromDeletedBooking.getBooking().getId()));
        var testContainer4 = createContainer(String.valueOf(captureSessionFromDeletedBooking2.getBooking().getId()));

        testContainer1.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("I_AM_NOT_A_SECTION_FILE".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );
        testContainer2.getBlobClient("gc_state").upload(
            new ByteArrayInputStream("I_AM_NOT_A_SECTION_FILE".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );
        testContainer3.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("gc stuff".getBytes()),
            "gc stuff".length()
        );
        testContainer4.getBlobClient("I_AM_NOT_A_SECTION_FILE").upload(
            new ByteArrayInputStream("gc stuff".getBytes()),
            "I_AM_NOT_A_SECTION_FILE".length()
        );

        System.out.println("Status before correction:" + captureSession1.getStatus() + " " + captureSession2.getStatus() + " " +
            captureSessionFromDeletedBooking.getStatus() + " " + captureSessionFromDeletedBooking2.getStatus());
        //Run correction task
        captureSessionStatusCorrectionTask.run();
        entityManager.clear();

        System.out.println("Status after correction:" + captureSession1.getStatus() + " " + captureSession2.getStatus() + " " +
                               captureSessionFromDeletedBooking.getStatus() + " " + captureSessionFromDeletedBooking2.getStatus());

        //Verify statuses have been corrected
        captureSession1 = entityManager.find(CaptureSession.class, captureSession1.getId());
        captureSession2 = entityManager.find(CaptureSession.class, captureSession2.getId());
        captureSessionFromDeletedBooking = entityManager.find(CaptureSession.class, captureSessionFromDeletedBooking.getId());
        captureSessionFromDeletedBooking2 = entityManager.find(CaptureSession.class, captureSessionFromDeletedBooking2.getId());

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
    }

    @Test
    @Transactional
    public void shouldUpdateUnusedCaptureSessionAssociatedWithDeletedUsersGracefully() {

        //Setup unused capture session data in database and storage
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase.setState(CaseState.OPEN);
        entityManager.persist(aCase);
        User deletedUser = HelperFactory.createUser("deleted", "user", "deleted@deleted.com",
                                                    Timestamp.from(Instant.now()), null, null);
        entityManager.persist(deletedUser);

        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        CaptureSession captureSessionFromDeletedUser = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-01 00:00:00"),
            deletedUser,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSessionFromDeletedUser);

        //Add the robot user that signs in to the database
        User robotUser = HelperFactory.createUser("robot", "user", cronUserEmail, null, null, null);
        entityManager.persist(robotUser);

        Role role = HelperFactory.createRole("Super User");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(robotUser, court, role, true, null, null, true);
        entityManager.persist(appAccess);

        robotUser.setAppAccess(new HashSet<>(List.of(appAccess)));
        entityManager.merge(robotUser);

        entityManager.flush();

        var testContainer3 = createContainer(String.valueOf(captureSessionFromDeletedUser.getBooking().getId()));

        testContainer3.getBlobClient("i_am_not_a_section_file").upload(
            new ByteArrayInputStream("i_am_not_a_section_file".getBytes()),
            "i_am_not_a_section_file".length()
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
    }
}
