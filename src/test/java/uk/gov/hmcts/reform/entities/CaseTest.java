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
import uk.gov.hmcts.reform.preapi.enums.CourtType;

@SpringBootTest(classes = Application.class)
public class CaseTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveCase() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);
        entityManager.flush();

        Case retrievedCase = entityManager.find(Case.class, testCase.getId());

        assertEquals(testCase.getId(), retrievedCase.getId());
        assertEquals(testCase.getCourt(), retrievedCase.getCourt());
        assertEquals(testCase.getCaseRef(), retrievedCase.getCaseRef());
        assertEquals(testCase.isTest(), retrievedCase.isTest());
        assertEquals(testCase.isDeleted(), retrievedCase.isDeleted());
        assertEquals(testCase.getCreatedOn(), retrievedCase.getCreatedOn());
        assertEquals(testCase.getModifiedOn(), retrievedCase.getModifiedOn());
    }
}
