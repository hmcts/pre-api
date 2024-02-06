package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
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

    @Transactional
    @Test
    public void updateCaseParticipants() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

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
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

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
