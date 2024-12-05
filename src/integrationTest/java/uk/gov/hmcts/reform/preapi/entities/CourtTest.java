package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class CourtTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveCourt() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        User user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "Test123");
        entityManager.persist(court);
        entityManager.flush();

        Court retrievedCourt = entityManager.find(Court.class, court.getId());

        assertEquals(court.getId(), retrievedCourt.getId(), "Id should match");
        assertEquals(court.getCourtType(), retrievedCourt.getCourtType(), "Court type should match");
        assertEquals(court.getName(), retrievedCourt.getName(), "Name should match");
        assertEquals(court.getLocationCode(), retrievedCourt.getLocationCode(), "Location codes should match");
    }
}
