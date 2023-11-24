package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Courtroom;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

@SpringBootTest(classes = Application.class)
public class CourtroomTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveCourtroom() {
        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);

        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", "Test123");
        entityManager.persist(court);

        Room testRoom = new Room();
        testRoom.setName("TestRoom");
        entityManager.persist(testRoom);

        Courtroom testCourtroom = new Courtroom();
        testCourtroom.setCourt(court);
        testCourtroom.setRoom(testRoom);
        entityManager.persist(testCourtroom);
        entityManager.flush();

        Courtroom retrievedCourtroom = entityManager.find(Courtroom.class, testCourtroom.getId());

        assertEquals(testCourtroom.getId(), retrievedCourtroom.getId());
        assertEquals(testCourtroom.getCourt(), retrievedCourtroom.getCourt());
        assertEquals(testCourtroom.getRoom(), retrievedCourtroom.getRoom());
    }
}
