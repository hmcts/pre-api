package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.CaptureSessionNotDeletedException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseServiceIT extends IntegrationTestBase {
    @Autowired
    private CaseService caseService;

    @Transactional
    @Test
    void searchCasesAsAdmin() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var caseEntity2 = HelperFactory.createCase(court, "CASE54321", true, Timestamp.from(Instant.now()));
        entityManager.persist(caseEntity2);

        var participant1 = HelperFactory.createParticipant(caseEntity, ParticipantType.WITNESS, "Example", "1", null);
        entityManager.persist(participant1);
        var participant2 = HelperFactory.createParticipant(caseEntity, ParticipantType.DEFENDANT, "Example", "2", null);
        entityManager.persist(participant2);
        caseEntity.setParticipants(new HashSet<>(Set.of(participant1)));
        caseEntity2.setParticipants(new HashSet<>(Set.of(participant2)));
        entityManager.persist(caseEntity);
        entityManager.persist(caseEntity2);

        var cases = caseService.searchBy(null, null, false, Pageable.unpaged()).toList();
        Assertions.assertEquals(cases.size(), 1);
        Assertions.assertEquals(cases.getFirst().getId(), caseEntity.getId());

        var cases2 = caseService.searchBy(null, null, true, Pageable.unpaged()).toList();
        Assertions.assertEquals(cases2.size(), 2);
        assertTrue(cases2.stream().anyMatch(caseDTO -> caseDTO.getId().equals(caseEntity.getId())));
        assertTrue(cases2.stream().anyMatch(caseDTO -> caseDTO.getId().equals(caseEntity2.getId())));
    }

    @Transactional
    @Test
    void searchCasesAsNonAdmin() {
        mockNonAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var caseEntity2 = HelperFactory.createCase(court, "CASE54321", true, Timestamp.from(Instant.now()));
        entityManager.persist(caseEntity2);

        var participant1 = HelperFactory.createParticipant(caseEntity, ParticipantType.WITNESS, "Example", "1", null);
        entityManager.persist(participant1);
        var participant2 = HelperFactory.createParticipant(caseEntity, ParticipantType.DEFENDANT, "Example", "2", null);
        entityManager.persist(participant2);
        caseEntity.setParticipants(new HashSet<>(Set.of(participant1)));
        caseEntity2.setParticipants(new HashSet<>(Set.of(participant2)));
        entityManager.persist(caseEntity);
        entityManager.persist(caseEntity2);

        var cases = caseService.searchBy(null, null, false, Pageable.unpaged()).toList();
        Assertions.assertEquals(cases.size(), 1);
        Assertions.assertEquals(cases.getFirst().getId(), caseEntity.getId());

        var message = Assertions.assertThrows(
            AccessDeniedException.class,
            () -> caseService.searchBy(null, null, true, Pageable.unpaged())
        ).getMessage();

        Assertions.assertEquals(message, "Access Denied");
    }

    @Transactional
    @Test
    void updateCaseParticipants() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var participant1 = HelperFactory.createParticipant(caseEntity, ParticipantType.WITNESS, "Example", "1", null);
        entityManager.persist(participant1);
        var participant2 = HelperFactory.createParticipant(caseEntity, ParticipantType.DEFENDANT, "Example", "2", null);
        entityManager.persist(participant2);
        var participant3 = HelperFactory.createParticipant(caseEntity, ParticipantType.WITNESS, "Example", "3", null);
        entityManager.persist(participant3);
        caseEntity.setParticipants(new HashSet<>(Set.of(participant1, participant2, participant3)));
        entityManager.persist(caseEntity);

        var updateCaseDTO = new CreateCaseDTO(caseEntity);
        updateCaseDTO.setParticipants(Set.of(participant1, participant2)
                                          .stream()
                                          .map(CreateParticipantDTO::new)
                                          .collect(Collectors.toSet()));

        caseService.upsert(updateCaseDTO);

        entityManager.flush();
        entityManager.refresh(court);
        entityManager.refresh(caseEntity);
        entityManager.refresh(participant1);
        entityManager.refresh(participant2);
        entityManager.refresh(participant3);
        entityManager.refresh(caseEntity);

        Assertions.assertNull(participant1.getDeletedAt());
        Assertions.assertEquals(participant1.getCaseId().getId(), caseEntity.getId());
        Assertions.assertNull(participant2.getDeletedAt());
        Assertions.assertEquals(participant2.getCaseId().getId(), caseEntity.getId());
        Assertions.assertNotNull(participant3.getDeletedAt());
        Assertions.assertEquals(participant3.getCaseId().getId(), caseEntity.getId());

        var foundCase = caseService.findById(caseEntity.getId());
        var participantIds = foundCase
            .getParticipants()
            .stream()
            .map(ParticipantDTO::getId)
            .collect(Collectors.toSet());

        assertTrue(participantIds.contains(participant1.getId()));
        assertTrue(participantIds.contains(participant2.getId()));
        Assertions.assertFalse(participantIds.contains(participant3.getId()));
    }

    @Transactional
    @Test
    void testCascadeDelete() {
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
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording);

        var message = Assertions.assertThrows(
            CaptureSessionNotDeletedException.class,
            () ->  caseService.deleteById(caseEntity.getId())
        ).getMessage();

        Assertions.assertEquals(message, "Cannot delete because an associated recording has not been deleted.");

        entityManager.refresh(caseEntity);
        entityManager.refresh(booking);
        entityManager.refresh(captureSession);
        entityManager.refresh(recording);

        Assertions.assertNull(caseEntity.getDeletedAt());
        Assertions.assertNull(booking.getDeletedAt());
        Assertions.assertNull(captureSession.getDeletedAt());
        Assertions.assertNull(recording.getDeletedAt());

        recording.setDeletedAt(Timestamp.from(Instant.now()));
        entityManager.persist(recording);
        entityManager.flush();
        entityManager.refresh(recording);

        caseService.deleteById(caseEntity.getId());

        entityManager.refresh(caseEntity);
        entityManager.refresh(booking);
        entityManager.refresh(captureSession);
        entityManager.refresh(recording);

        Assertions.assertNotNull(caseEntity.getDeletedAt());
        Assertions.assertNotNull(booking.getDeletedAt());
        Assertions.assertNotNull(captureSession.getDeletedAt());
        Assertions.assertNotNull(recording.getDeletedAt());
    }

    @Transactional
    @Test
    void undeleteCase() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, Timestamp.from(Instant.now()));
        entityManager.persist(caseEntity);

        caseService.undelete(caseEntity.getId());
        entityManager.flush();
        entityManager.refresh(caseEntity);
        Assertions.assertNull(caseEntity.getDeletedAt());

        caseService.undelete(caseEntity.getId());
        entityManager.flush();
        entityManager.refresh(caseEntity);
        Assertions.assertNull(caseEntity.getDeletedAt());

        var randomId = UUID.randomUUID();
        var message = Assertions.assertThrows(
            NotFoundException.class,
            () -> caseService.undelete(randomId)
        ).getMessage();
        Assertions.assertEquals(message, "Not found: Case: " + randomId);
    }

    @Transactional
    @Test
    void createCase() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var createDto = new CreateCaseDTO();
        createDto.setId(UUID.randomUUID());
        createDto.setCourtId(court.getId());
        createDto.setReference("1234567890");
        createDto.setParticipants(Set.of());
        createDto.setTest(false);

        var response = caseService.upsert(createDto);
        entityManager.flush();
        Assertions.assertEquals(response, UpsertResult.CREATED);

        var newCase = caseService.findById(createDto.getId());
        Assertions.assertNotNull(newCase);
    }

    @Transactional
    @Test
    void createCaseWithCaseReferenceAndCourtAlreadyExisting() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var dto = new CreateCaseDTO(caseEntity);
        dto.setId(UUID.randomUUID());

        var message = Assertions.assertThrows(
            ConflictException.class,
            () -> caseService.upsert(dto)
        ).getMessage();
        Assertions.assertEquals("Conflict: Case reference is already in use for this court", message);
    }

    @Test
    @Transactional
    void closeCaseWithEmptyCaptureSessions() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking1 = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking1);
        var booking2 = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking2);
        var booking3 = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking3);

        var captureSessionNoRecording = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.NO_RECORDING,
            null
        );
        entityManager.persist(captureSessionNoRecording);
        var captureSessionFailure = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.FAILURE,
            null
        );
        entityManager.persist(captureSessionFailure);
        var captureSessionRecordingAvailable = HelperFactory.createCaptureSession(
            booking3,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSessionRecordingAvailable);
        entityManager.flush();

        entityManager.refresh(booking1);
        entityManager.refresh(booking2);
        entityManager.refresh(booking3);

        caseService.onCaseClosed(caseEntity);

        assertThat(booking1.getDeletedAt()).isNotNull();
        assertThat(captureSessionNoRecording.getDeletedAt()).isNotNull();
        assertThat(booking2.getDeletedAt()).isNotNull();
        assertThat(captureSessionFailure.getDeletedAt()).isNotNull();
        assertThat(booking3.getDeletedAt()).isNull();
        assertThat(captureSessionRecordingAvailable.getDeletedAt()).isNull();
    }

    @Test
    @Transactional
    void setCaseToPendingClosureWithCompletedEditRequest() {
        mockAdminUser();

        var court = persistCourt();
        var caseEntity = persistCase(court);
        var scheduledFor = Timestamp.from(Instant.now().minusSeconds(3600));
        var booking = persistBooking(caseEntity, scheduledFor);
        var recording = persistCaptureSessionAndRecording(booking);
        var startedAt = Timestamp.from(scheduledFor.toInstant().plusSeconds(1800));
        persistEditRequest(startedAt, recording, EditRequestStatus.COMPLETE);

        entityManager.flush();
        entityManager.refresh(booking);

        setCaseToPendingClosure(caseEntity, court);

        assertThat(caseEntity.getState()).isEqualTo(CaseState.PENDING_CLOSURE);
        assertThat(caseEntity.getClosedAt()).isNotNull();
    }

    @Test
    @Transactional
    void setCaseToPendingClosureWithIncompleteEditRequest() {
        mockAdminUser();

        var court = persistCourt();
        var caseEntity = persistCase(court);
        var scheduledFor = Timestamp.from(Instant.now().minusSeconds(3600));
        var booking = persistBooking(caseEntity, scheduledFor);
        var recording = persistCaptureSessionAndRecording(booking);
        var startedAt = Timestamp.from(scheduledFor.toInstant().plusSeconds(1800));
        persistEditRequest(startedAt, recording, EditRequestStatus.PROCESSING);

        entityManager.flush();
        entityManager.refresh(booking);

        Assertions.assertThrows(ResourceInWrongStateException.class, () -> setCaseToPendingClosure(caseEntity, court));

        assertThat(caseEntity.getState()).isEqualTo(CaseState.OPEN);
        assertThat(caseEntity.getClosedAt()).isNull();
    }

    private Court persistCourt() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);
        return court;
    }

    private Case persistCase(Court court) {
        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);
        return caseEntity;
    }

    private Booking persistBooking(Case caseEntity, Timestamp scheduledFor) {
        var booking = HelperFactory.createBooking(caseEntity, scheduledFor, null);
        entityManager.persist(booking);
        return booking;
    }

    private Recording persistCaptureSessionAndRecording(Booking booking) {
        var captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording);

        return recording;
    }

    private void persistEditRequest(Timestamp startedAt, Recording recording, EditRequestStatus status) {
        var editRequestUser = HelperFactory.createDefaultTestUser();
        entityManager.persist(editRequestUser);

        var finishedAt = Timestamp.from(startedAt.toInstant().plusSeconds(60));
        var editRequest = HelperFactory.createEditRequest(
            recording,
            "{}",
            status,
            editRequestUser,
            startedAt,
            finishedAt,
            null,
            null,
            null,
            null
        );
        entityManager.persist(editRequest);
    }

    private void setCaseToPendingClosure(Case caseEntity, Court court) {
        var upsertCaseDto = new CreateCaseDTO();
        upsertCaseDto.setId(caseEntity.getId());
        upsertCaseDto.setCourtId(court.getId());
        upsertCaseDto.setReference(caseEntity.getReference());
        upsertCaseDto.setParticipants(Set.of());
        upsertCaseDto.setTest(false);
        upsertCaseDto.setState(CaseState.PENDING_CLOSURE);
        upsertCaseDto.setClosedAt(Timestamp.from(Instant.now()));

        caseService.upsert(upsertCaseDto);
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=false")
    class WithMigratedDataDisabled {
        @Autowired
        private CaseService caseService;

        @Autowired
        protected EntityManager entityManager;

        @AfterEach
        void tearDown() {
            try {
                entityManager.clear();
                entityManager.flush();
            } catch (Exception ignored) {
                // ignored
            }
        }

        @Test
        @Transactional
        void searchCasesDisableMigratedDataToggleNonSuperUser() {
            mockNonAdminUser();

            Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
            entityManager.persist(court);
            Case aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase1.setOrigin(RecordingOrigin.PRE);
            entityManager.persist(aCase1);
            Case aCase2 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase2.setOrigin(RecordingOrigin.VODAFONE);
            entityManager.persist(aCase2);
            entityManager.flush();

            Page<CaseDTO> results = caseService.searchBy(null, null, false, null);

            assertThat(results.getTotalElements()).isEqualTo(1);
            CaseDTO foundCase = results.getContent().getFirst();
            assertThat(foundCase.getId()).isEqualTo(aCase1.getId());
            assertThat(foundCase.getOrigin()).isEqualTo(RecordingOrigin.PRE);
        }

        @Test
        @Transactional
        void searchCasesDisableMigratedDataToggleSuperUser() {
            mockAdminUser();

            Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
            entityManager.persist(court);
            Case aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase1.setOrigin(RecordingOrigin.PRE);
            entityManager.persist(aCase1);
            Case aCase2 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase2.setOrigin(RecordingOrigin.VODAFONE);
            entityManager.persist(aCase2);
            entityManager.flush();

            Page<CaseDTO> results = caseService.searchBy(null, null, false, null);

            assertThat(results.getTotalElements()).isEqualTo(2);
            assertTrue(results.getContent().stream()
                           .map(CaseDTO::getId)
                           .anyMatch(caseId -> caseId.equals(aCase1.getId())));
            assertTrue(results.getContent().stream()
                           .map(CaseDTO::getId)
                           .anyMatch(caseId -> caseId.equals(aCase2.getId())));
        }
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=true")
    class WithMigratedDataEnabled {
        @Autowired
        private CaseService caseService;

        @Autowired
        protected EntityManager entityManager;

        @AfterEach
        void tearDown() {
            try {
                entityManager.clear();
                entityManager.flush();
            } catch (Exception ignored) {
                // ignored
            }
        }

        @Test
        @Transactional
        void searchCasesEnableMigratedDataToggleNonSuperUser() {
            mockNonAdminUser();

            Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
            entityManager.persist(court);
            Case aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase1.setOrigin(RecordingOrigin.PRE);
            entityManager.persist(aCase1);
            Case aCase2 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase2.setOrigin(RecordingOrigin.VODAFONE);
            entityManager.persist(aCase2);
            entityManager.flush();

            Page<CaseDTO> results = caseService.searchBy(null, null, false, null);

            assertThat(results.getTotalElements()).isEqualTo(2);
            assertTrue(results.getContent().stream()
                           .map(CaseDTO::getId)
                           .anyMatch(caseId -> caseId.equals(aCase1.getId())));
            assertTrue(results.getContent().stream()
                           .map(CaseDTO::getId)
                           .anyMatch(caseId -> caseId.equals(aCase2.getId())));
        }

        @Test
        @Transactional
        void searchCasesEnableMigratedDataToggleSuperUser() {
            mockAdminUser();

            Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
            entityManager.persist(court);
            Case aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase1.setOrigin(RecordingOrigin.PRE);
            entityManager.persist(aCase1);
            Case aCase2 = HelperFactory.createCase(court, "CASE12345", true, null);
            aCase2.setOrigin(RecordingOrigin.VODAFONE);
            entityManager.persist(aCase2);
            entityManager.flush();

            Page<CaseDTO> results = caseService.searchBy(null, null, false, null);
            assertThat(results.getTotalElements()).isEqualTo(2);
        }
    }
}
