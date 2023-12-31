package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RoomTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSaveAndRetrieveRoom() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Room testRoom = new Room();
        testRoom.setName("TestRoomName");
        entityManager.persist(testRoom);
        entityManager.flush();

        Room retrievedRoom = entityManager.find(Room.class, testRoom.getId());

        assertEquals(testRoom.getId(), retrievedRoom.getId(), "Id should match");
        assertEquals(testRoom.getName(), retrievedRoom.getName(), "Name should match");
    }
}
