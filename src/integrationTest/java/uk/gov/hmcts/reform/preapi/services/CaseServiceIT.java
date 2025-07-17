package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.CaptureSessionNotDeletedException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class CaseServiceIT extends IntegrationTestBase {
    @Autowired
    private CaseService caseService;

    @Autowired
    private RecordingRepository recordingRepository;

    @Transactional
    @Test
    public void searchCasesAsAdmin() {
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
        Assertions.assertTrue(cases2.stream().anyMatch(caseDTO -> caseDTO.getId().equals(caseEntity.getId())));
        Assertions.assertTrue(cases2.stream().anyMatch(caseDTO -> caseDTO.getId().equals(caseEntity2.getId())));
    }

    @Transactional
    @Test
    public void searchCasesAsNonAdmin() {
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
    public void updateCaseParticipants() {
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

        Assertions.assertTrue(participantIds.contains(participant1.getId()));
        Assertions.assertTrue(participantIds.contains(participant2.getId()));
        Assertions.assertFalse(participantIds.contains(participant3.getId()));
    }

    @Transactional
    @Test
    public void testCascadeDelete() {
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

        Assertions.assertEquals(message, "Cannot delete because and associated recording has not been deleted.");

        entityManager.refresh(caseEntity);
        entityManager.refresh(booking);
        entityManager.refresh(captureSession);
        entityManager.refresh(recording);

        Assertions.assertNull(caseEntity.getDeletedAt());
        Assertions.assertNull(booking.getDeletedAt());
        Assertions.assertNull(captureSession.getDeletedAt());
        Assertions.assertNull(recording.getDeletedAt());

        recording.setDeletedAt(Timestamp.from(Instant.now()));
        recordingRepository.saveAndFlush(recording);
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
    public void undeleteCase() {
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
}
