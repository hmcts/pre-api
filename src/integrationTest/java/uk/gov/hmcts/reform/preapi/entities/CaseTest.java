package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class CaseDTOTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveCase() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testCase);
        entityManager.flush();

        Case retrievedCase = entityManager.find(Case.class, testCase.getId());

        assertEquals(testCase.getId(), retrievedCase.getId(), "Id should match");
        assertEquals(testCase.getCourt(), retrievedCase.getCourt(), "Court should match");
        assertEquals(testCase.getReference(), retrievedCase.getReference(), "CaseDTO reference should match");
        assertEquals(testCase.isTest(), retrievedCase.isTest(), "Test status should match");
        assertEquals(testCase.getDeletedAt(), retrievedCase.getDeletedAt(), "Deleted at should match");
        assertEquals(testCase.getCreatedAt(), retrievedCase.getCreatedAt(), "Created at should match");
        assertEquals(testCase.getModifiedAt(), retrievedCase.getModifiedAt(), "Modified at should match");
    }
}
