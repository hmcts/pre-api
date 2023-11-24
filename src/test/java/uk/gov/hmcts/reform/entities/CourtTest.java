package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

@SpringBootTest(classes = Application.class)
public class CourtTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveCourt() {
        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);

        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", "Test123");
        entityManager.persist(court);
        entityManager.flush();

        Court retrievedCourt = entityManager.find(Court.class, court.getId());

        assertEquals(court.getId(), retrievedCourt.getId());
        assertEquals(court.getCourtType(), retrievedCourt.getCourtType());
        assertEquals(court.getName(), retrievedCourt.getName());
        assertEquals(court.getLocationCode(), retrievedCourt.getLocationCode());
        assertEquals(court.getRegions(), retrievedCourt.getRegions());
    }
}
