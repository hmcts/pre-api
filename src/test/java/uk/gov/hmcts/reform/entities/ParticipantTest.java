package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

@SpringBootTest(classes = Application.class)
public class ParticipantTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveParticipant() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Participant testParticipant = HelperFactory.createParticipant(testCase, ParticipantType.defendant, "Test", "Participant", false);

        entityManager.persist(testParticipant);
        entityManager.flush();

        Participant retrievedParticipant = entityManager.find(Participant.class, testParticipant.getId());

        assertEquals(testParticipant.getId(), retrievedParticipant.getId());
        assertEquals(testParticipant.getCaseId(), retrievedParticipant.getCaseId());
        assertEquals(testParticipant.getParticipantType(), retrievedParticipant.getParticipantType());
        assertEquals(testParticipant.getFirstName(), retrievedParticipant.getFirstName());
        assertEquals(testParticipant.getLastName(), retrievedParticipant.getLastName());
        assertEquals(testParticipant.isDeleted(), retrievedParticipant.isDeleted());
        assertEquals(testParticipant.getCreatedOn(), retrievedParticipant.getCreatedOn());
        assertEquals(testParticipant.getModifiedOn(), retrievedParticipant.getModifiedOn());
    }
}
