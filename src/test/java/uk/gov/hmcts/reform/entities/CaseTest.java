package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class CaseTest {

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

        assertEquals(testCase.getId(), retrievedCase.getId(), "Id should match");
        assertEquals(testCase.getCourt(), retrievedCase.getCourt(), "Court should match");
        assertEquals(testCase.getCaseRef(), retrievedCase.getCaseRef(), "Case reference should match");
        assertEquals(testCase.isTest(), retrievedCase.isTest(), "Test status should match");
        assertEquals(testCase.isDeleted(), retrievedCase.isDeleted(), "Deleted status should match");
        assertEquals(testCase.getCreatedOn(), retrievedCase.getCreatedOn(), "Created on should match");
        assertEquals(testCase.getModifiedOn(), retrievedCase.getModifiedOn(), "Modified on should match");
    }
}
