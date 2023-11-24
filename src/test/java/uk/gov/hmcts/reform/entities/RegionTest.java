package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;

@SpringBootTest(classes = Application.class)
public class RegionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveRegion() {
        Region testRegion = new Region();
        testRegion.setName("TestRegionName");
        entityManager.persist(testRegion);
        entityManager.flush();

        Region retrievedRegion = entityManager.find(Region.class, testRegion.getId());

        assertEquals(testRegion.getId(), retrievedRegion.getId());
        assertEquals(testRegion.getName(), retrievedRegion.getName());
        assertEquals(testRegion.getCourts(), retrievedRegion.getCourts());
    }
}
