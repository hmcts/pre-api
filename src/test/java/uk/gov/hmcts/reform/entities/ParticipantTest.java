package uk.gov.hmcts.reform.entities;

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

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class ParticipantTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveParticipant() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testCase);

        Participant testParticipant = HelperFactory.createParticipant(
            testCase,
            ParticipantType.defendant,
            "Test",
            "Participant",
            new Timestamp(System.currentTimeMillis())
        );

        entityManager.persist(testParticipant);
        entityManager.flush();

        Participant retrievedParticipant = entityManager.find(Participant.class, testParticipant.getId());

        assertEquals(testParticipant.getId(), retrievedParticipant.getId(), "Id should match");
        assertEquals(testParticipant.getCaseId(), retrievedParticipant.getCaseId(), "Case should match");
        assertEquals(
            testParticipant.getParticipantType(),
            retrievedParticipant.getParticipantType(),
            "Participant type should match"
        );
        assertEquals(testParticipant.getFirstName(), retrievedParticipant.getFirstName(), "First names should match");
        assertEquals(testParticipant.getLastName(), retrievedParticipant.getLastName(), "Last name should match");
        assertEquals(testParticipant.getDeletedAt(), retrievedParticipant.getDeletedAt(), "Deleted at should match");
        assertEquals(testParticipant.getCreatedAt(), retrievedParticipant.getCreatedAt(), "Created at should match");
        assertEquals(testParticipant.getModifiedAt(), retrievedParticipant.getModifiedAt(), "Modified at should match");
    }
}
