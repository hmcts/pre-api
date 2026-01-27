package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CaptureSessionServiceIT extends IntegrationTestBase {

    @Autowired
    private CaptureSessionService captureSessionService;

    @BeforeEach
    public void setUp() {
        captureSessionService.setEnableMigratedData(false);
    }

    @Test
    @Transactional
    void searchCaptureSessionsEnableMigratedDataToggleNonSuperUser() {
        mockNonAdminUser();

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase1.setOrigin(RecordingOrigin.PRE);
        entityManager.persist(aCase1);
        Case aCase2 = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase2.setOrigin(RecordingOrigin.VODAFONE);
        entityManager.persist(aCase2);
        Booking booking1 = HelperFactory.createBooking(aCase1, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking1);
        Booking booking2 = HelperFactory.createBooking(aCase2, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession1);
        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.VODAFONE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession2);
        CaptureSession captureSession3 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession3);
        entityManager.flush();

        // enableMigratedData = false
        Page<CaptureSessionDTO> results = captureSessionService.searchBy(
            null,
            null,
            null,
            null,
            Optional.empty(),
            null,
            null
        );

        assertThat(results.getTotalElements()).isEqualTo(1);
        CaptureSessionDTO foundCaptureSession = results.getContent().getFirst();
        assertThat(foundCaptureSession.getId()).isEqualTo(captureSession1.getId());
        assertThat(foundCaptureSession.getOrigin()).isEqualTo(RecordingOrigin.PRE);

        captureSessionService.setEnableMigratedData(true);
        Page<CaptureSessionDTO> results2 = captureSessionService.searchBy(
            null,
            null,
            null,
            null,
            Optional.empty(),
            null,
            null
        );

        assertThat(results2.getTotalElements()).isEqualTo(3);
        assertTrue(results2.getContent().stream()
                       .map(CaptureSessionDTO::getId)
                       .anyMatch(id -> id.equals(captureSession1.getId())));
        assertTrue(results2.getContent().stream()
                       .map(CaptureSessionDTO::getId)
                       .anyMatch(id -> id.equals(captureSession2.getId())));
        assertTrue(results2.getContent().stream()
                       .map(CaptureSessionDTO::getId)
                       .anyMatch(id -> id.equals(captureSession3.getId())));
    }

    @Test
    @Transactional
    void searchCaptureSessionsEnableMigratedDataToggleSuperUser() {
        mockAdminUser();

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase1.setOrigin(RecordingOrigin.PRE);
        entityManager.persist(aCase1);
        Case aCase2 = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase2.setOrigin(RecordingOrigin.VODAFONE);
        entityManager.persist(aCase2);
        Booking booking1 = HelperFactory.createBooking(aCase1, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking1);
        Booking booking2 = HelperFactory.createBooking(aCase2, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking2);
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession1);
        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.VODAFONE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession2);
        CaptureSession captureSession3 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession3);
        entityManager.flush();

        // enableMigratedData = false
        Page<CaptureSessionDTO> results = captureSessionService.searchBy(
            null,
            null,
            null,
            null,
            Optional.empty(),
            null,
            null
        );

        assertThat(results.getTotalElements()).isEqualTo(3);
        assertTrue(results.getContent().stream()
                       .map(CaptureSessionDTO::getId)
                       .anyMatch(id -> id.equals(captureSession1.getId())));
        assertTrue(results.getContent().stream()
                       .map(CaptureSessionDTO::getId)
                       .anyMatch(id -> id.equals(captureSession2.getId())));
        assertTrue(results.getContent().stream()
                       .map(CaptureSessionDTO::getId)
                       .anyMatch(id -> id.equals(captureSession3.getId())));

        captureSessionService.setEnableMigratedData(true);
        Page<CaptureSessionDTO> results2 = captureSessionService.searchBy(
            null,
            null,
            null,
            null,
            Optional.empty(),
            null,
            null
        );

        assertThat(results2.getTotalElements()).isEqualTo(3);
    }

    @Test
    @Transactional
    void shouldFindFailedCaptureSessionsStartedBetweenSpecificDates() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture session within the specified dates
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-06 10:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-5 10:00:00"),
            null,
            Timestamp.valueOf("2025-10-11 18:00:00"),
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession2);

        CaptureSession captureSession3 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-09 10:00:00"),
            null,
            Timestamp.valueOf("2025-10-11 18:00:00"),
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession3);

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).hasSize(3)
            .extracting("id")
            .containsExactlyInAnyOrder(captureSession1.getId(), captureSession2.getId(), captureSession3.getId());
    }

    @Test
    @Transactional
    void shouldNotReturnFailedCaptureSessionsOutsideDateRange() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture session before the date range
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-09-30 10:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSession1);

        // Capture session after the date range
        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-04 10:00:00"),
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession2);

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).isEmpty();
    }

    @Test
    @Transactional
    void shouldNotReturnFailedCaptureSessionsThatHaveNullStartedAtDate() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture session with null startedAt date
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession1);

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).isEmpty();
    }

    @Test
    @Transactional
    void shouldNotReturnFailedCaptureSessionsWithinDateRangeThatHaveBeenDeleted() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture sessions within the specified dates but marked as deleted
        CaptureSession captureSession1 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-10-06 10:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            Timestamp.valueOf("2025-10-07 10:00:00")
        );
        entityManager.persist(captureSession1);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.valueOf("2025-11-01 10:00:00"),
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            Timestamp.valueOf("2025-11-01 10:00:00")
        );
        entityManager.persist(captureSession2);

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).isEmpty();
    }

    @Test
    @Transactional
    void shouldReturnFailedCaptureSessionsAtTheEdgesOfDateRange() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture session at the very edge of start date
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

        // Capture session on the very edge of end date
        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
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

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).hasSize(2)
            .extracting("id")
            .containsExactlyInAnyOrder(captureSession1.getId(), captureSession2.getId());
    }

    @Test
    @Transactional
    void shouldNotReturnFailedCaptureSessionsAssociatedWithClosedCases() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase.setState(CaseState.CLOSED);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture session at the very edge of start date
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

        // Capture session on the very edge of end date
        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
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

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).isEmpty();
    }

    @Test
    @Transactional
    void shouldNotReturnFailedCaptureSessionsAssociatedWithPendingCloseCases() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase.setState(CaseState.PENDING_CLOSURE);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        // Capture session at the very edge of start date
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

        // Capture session on the very edge of end date
        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
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

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).isEmpty();
    }

    @Test
    @Transactional
    void shouldReturnFailedCaptureSessionsAssociatedWithDeletedCases() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        aCase.setDeletedAt(Timestamp.valueOf("2025-10-01 00:00:00"));
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        entityManager.persist(booking);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
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

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).hasSize(1)
            .extracting("id")
            .containsExactlyInAnyOrder(captureSession2.getId());
    }

    @Test
    @Transactional
    void shouldReturnFailedCaptureSessionsAssociatedWithDeletedBookings() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 3);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        Case aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);
        Booking booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null, null);
        booking.setDeletedAt(Timestamp.valueOf("2025-10-01 00:00:00"));
        entityManager.persist(booking);

        CaptureSession captureSession2 = HelperFactory.createCaptureSession(
            booking,
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

        entityManager.flush();

        List<CaptureSession> results =
            captureSessionService.findFailedCaptureSessionsStartedBetween(startDate, endDate);

        assertThat(results).hasSize(1)
            .extracting("id")
            .containsExactlyInAnyOrder(captureSession2.getId());
    }
}
