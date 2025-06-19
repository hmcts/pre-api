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
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
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
}
