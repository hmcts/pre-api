package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RegionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSaveAndRetrieveRegion() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Region testRegion = new Region();
        testRegion.setName("TestRegionName");
        entityManager.persist(testRegion);
        entityManager.flush();

        Region retrievedRegion = entityManager.find(Region.class, testRegion.getId());

        assertEquals(testRegion.getId(), retrievedRegion.getId(), "Id should match");
        assertEquals(testRegion.getName(), retrievedRegion.getName(), "Name should match");
    }
}
