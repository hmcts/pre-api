package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.exception.RecordingNotDeletedException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class)
public class CaseServiceIT {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CaseService caseService;
    @Autowired
    private RecordingRepository recordingRepository;

    public static void mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

    public static void mockNonAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.isPortalUser()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

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
            null,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "url", "filename",null);
        entityManager.persist(recording);

        var message = Assertions.assertThrows(
            RecordingNotDeletedException.class,
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
}
