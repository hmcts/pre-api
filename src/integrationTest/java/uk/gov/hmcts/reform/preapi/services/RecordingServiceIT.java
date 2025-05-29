package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RecordingServiceIT extends IntegrationTestBase {
    @Autowired
    private RecordingService recordingService;

    @BeforeEach
    public void setUp() {
        recordingService.setEnableMigratedData(false);
    }

    @Transactional
    @Test
    void searchRecordingsAsAdmin() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(
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
        entityManager.persist(captureSession);

        var recording1 = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording1);

        var recording2 = HelperFactory.createRecording(
            captureSession,
            null,
            2,
            "filename",
            Timestamp.from(Instant.now())
        );
        entityManager.persist(recording2);

        var recordings1 = recordingService.findAll(new SearchRecordings(), false, Pageable.unpaged()).toList();
        Assertions.assertEquals(recordings1.size(), 1);
        Assertions.assertEquals(recordings1.getFirst().getId(), recording1.getId());

        var recordings2 = recordingService.findAll(new SearchRecordings(), true, Pageable.unpaged()).toList();
        Assertions.assertEquals(recordings2.size(), 2);
        Assertions.assertTrue(recordings2.stream().anyMatch(recording -> recording.getId().equals(recording1.getId())));
        Assertions.assertTrue(recordings2.stream().anyMatch(recording -> recording.getId().equals(recording2.getId())));
    }

    @Transactional
    @Test
    void searchRecordingsAsNonAdmin() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        mockNonAdminUser(court.getId());

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(
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
        entityManager.persist(captureSession);

        var recording1 = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording1);

        var recording2 = HelperFactory.createRecording(
            captureSession,
            null,
            2,
            "filename",
            Timestamp.from(Instant.now())
        );
        entityManager.persist(recording2);

        var recordings1 = recordingService.findAll(new SearchRecordings(), false, Pageable.unpaged()).toList();
        Assertions.assertEquals(recordings1.size(), 1);
        Assertions.assertEquals(recordings1.getFirst().getId(), recording1.getId());

        var message = Assertions.assertThrows(
            AccessDeniedException.class,
            () -> recordingService.findAll(new SearchRecordings(), true, Pageable.unpaged()).toList()
        ).getMessage();
        Assertions.assertEquals(message, "Access Denied");
    }

    @Test
    @Transactional
    void getNextVersionNumberSuccess() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        mockNonAdminUser(court.getId());

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(
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
        entityManager.persist(captureSession);

        var recording1 = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording1);

        var nextVersion1 = recordingService.getNextVersionNumber(recording1.getId());
        assertThat(nextVersion1).isEqualTo(2);

        var recording2 = HelperFactory.createRecording(
            captureSession,
            recording1,
            2,
            "filename",
            Timestamp.from(Instant.now())
        );
        entityManager.persist(recording2);
        var nextVersion2 = recordingService.getNextVersionNumber(recording1.getId());
        assertThat(nextVersion2).isEqualTo(3);
    }

    @Test
    @Transactional
    void searchRecordingsEnableMigratedDataToggleNonSuperUser() {
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
        Recording recording1 = HelperFactory.createRecording(captureSession1, null, 1, "", null);
        entityManager.persist(recording1);
        Recording recording2 = HelperFactory.createRecording(captureSession2, null, 1, "", null);
        entityManager.persist(recording2);
        Recording recording3 = HelperFactory.createRecording(captureSession3, null, 1, "", null);
        entityManager.persist(recording3);
        entityManager.flush();

        // enableMigratedData = false
        Page<RecordingDTO> results = recordingService.findAll(new SearchRecordings(), false, null);

        assertThat(results.getTotalElements()).isEqualTo(1);
        RecordingDTO foundRecording = results.getContent().getFirst();
        assertThat(foundRecording.getId()).isEqualTo(recording1.getId());
        assertThat(foundRecording.getCaptureSession().getOrigin()).isEqualTo(RecordingOrigin.PRE);

        recordingService.setEnableMigratedData(true);
        Page<RecordingDTO> results2 = recordingService.findAll(new SearchRecordings(), false, null);

        assertThat(results2.getTotalElements()).isEqualTo(3);
        assertTrue(results2.getContent().stream()
                       .map(RecordingDTO::getId)
                       .anyMatch(id -> id.equals(recording1.getId())));
        assertTrue(results2.getContent().stream()
                       .map(RecordingDTO::getId)
                       .anyMatch(id -> id.equals(recording2.getId())));
        assertTrue(results2.getContent().stream()
                       .map(RecordingDTO::getId)
                       .anyMatch(id -> id.equals(recording3.getId())));
    }

    @Test
    @Transactional
    void searchRecordingsEnableMigratedDataToggleSuperUser() {
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
        Recording recording1 = HelperFactory.createRecording(captureSession1, null, 1, "", null);
        entityManager.persist(recording1);
        Recording recording2 = HelperFactory.createRecording(captureSession2, null, 1, "", null);
        entityManager.persist(recording2);
        Recording recording3 = HelperFactory.createRecording(captureSession3, null, 1, "", null);
        entityManager.persist(recording3);
        entityManager.flush();

        // enableMigratedData = false
        Page<RecordingDTO> results = recordingService.findAll(new SearchRecordings(), false, null);

        assertThat(results.getTotalElements()).isEqualTo(3);
        assertTrue(results.getContent().stream()
                       .map(RecordingDTO::getId)
                       .anyMatch(id -> id.equals(recording1.getId())));
        assertTrue(results.getContent().stream()
                       .map(RecordingDTO::getId)
                       .anyMatch(id -> id.equals(recording2.getId())));
        assertTrue(results.getContent().stream()
                       .map(RecordingDTO::getId)
                       .anyMatch(id -> id.equals(recording3.getId())));

        recordingService.setEnableMigratedData(true);
        Page<RecordingDTO> results2 = recordingService.findAll(new SearchRecordings(), false, null);

        assertThat(results2.getTotalElements()).isEqualTo(3);
    }

    private static void mockNonAdminUser(UUID courtId) {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.getCourtId()).thenReturn(courtId);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }
}
