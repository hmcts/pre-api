package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecordingServiceIT extends IntegrationTestBase {
    @Autowired
    private RecordingService recordingService;

    @BeforeEach
    public void setUp() {
        recordingService.setEnableMigratedData(false);
    }

    @Test
    @Transactional
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

    @Test
    @Transactional
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

        var message = assertThrows(
            AccessDeniedException.class,
            () -> recordingService.findAll(new SearchRecordings(), true, Pageable.unpaged()).toList()
        ).getMessage();
        Assertions.assertEquals(message, "Access Denied");
    }

    @Test
    @Transactional
    void searchRecordingsByCaseOpenAsNonAdmin() {
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

        var search = new SearchRecordings();
        search.setCaseOpen(true);

        var recordings = recordingService.findAll(search, false, Pageable.unpaged()).toList();
        Assertions.assertEquals(1, recordings.size());
        Assertions.assertEquals(recordings.getFirst().getId(), recording1.getId());

        search.setCaseOpen(false);
        var message = Assertions.assertThrows(
            AccessDeniedException.class,
            () -> recordingService.findAll(search, true, Pageable.unpaged()).toList()
        ).getMessage();
        Assertions.assertEquals(message, "Access Denied");
    }

    @Test
    @Transactional
    void searchRecordingsByCaseOpenAsAdmin() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        mockAdminUser();

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
        entityManager.flush();

        var searchTrue = new SearchRecordings();
        searchTrue.setCaseOpen(true);
        var searchFalse = new SearchRecordings();
        searchFalse.setCaseOpen(false);

        var recordings1 = recordingService.findAll(searchTrue, false, Pageable.unpaged()).toList();
        Assertions.assertEquals(1, recordings1.size());
        Assertions.assertEquals(recordings1.getFirst().getId(), recording1.getId());
        var recordings2 = recordingService.findAll(searchFalse, false, Pageable.unpaged()).toList();
        Assertions.assertTrue(recordings2.isEmpty());

        // mark case as closed
        caseEntity.setState(CaseState.CLOSED);
        entityManager.persist(caseEntity);
        entityManager.flush();

        var recordings3 = recordingService.findAll(searchTrue, false, Pageable.unpaged()).toList();
        Assertions.assertTrue(recordings3.isEmpty());
        var recordings4 = recordingService.findAll(searchFalse, false, Pageable.unpaged()).toList();
        Assertions.assertEquals(1, recordings4.size());
        Assertions.assertEquals(recordings4.getFirst().getId(), recording1.getId());
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
    void findAllDurationNullReturnsOnlyRecordingsWithNullDuration() {
        mockAdminUser();

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        Case caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        Booking booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        CaptureSession captureSession = HelperFactory.createCaptureSession(
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

        Recording recordingWithNullDuration = HelperFactory.createRecording(
            captureSession, null, 1, "nullDurationFile", null);
        entityManager.persist(recordingWithNullDuration);

        Recording recordingWithDuration = HelperFactory.createRecording(
            captureSession,
            null,
            2,
            "withDurationFile",
            Timestamp.from(Instant.now())
        );
        entityManager.persist(recordingWithDuration);

        Recording deletedRecording = HelperFactory.createRecording(captureSession, null, 3, "deletedFile", null);
        deletedRecording.setDeletedAt(Timestamp.from(Instant.now()));
        entityManager.persist(deletedRecording);

        List<RecordingDTO> results = recordingService.findAllDurationNull();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(recordingWithNullDuration.getId());
    }

    @Test
    @Transactional
    @DisplayName("Should force upsert recording even if case in uneditable state")
    void forceUpsert() {
        mockAdminUser();

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        Case caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        caseEntity.setState(CaseState.PENDING_CLOSURE);
        entityManager.persist(caseEntity);

        Booking booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        CaptureSession captureSession = HelperFactory.createCaptureSession(
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

        Recording recordingWithNullDuration = HelperFactory.createRecording(
            captureSession, null, 1, "nullDurationFile", null);
        entityManager.persist(recordingWithNullDuration);
        entityManager.flush();

        CreateRecordingDTO updateDto = new CreateRecordingDTO();
        updateDto.setId(recordingWithNullDuration.getId());
        updateDto.setCaptureSessionId(captureSession.getId());
        updateDto.setVersion(1);
        updateDto.setFilename("updatedFilename");
        updateDto.setDuration(Duration.ofMinutes(3));

        assertThrows(
            ResourceInWrongStateException.class,
            () -> recordingService.upsert(updateDto)
        );
        assertThat(recordingService.forceUpsert(updateDto)).isEqualTo(UpsertResult.UPDATED);
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
