package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Room;

@SpringBootTest(classes = Application.class)
public class RoomTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveRoom() {
        Room testRoom = new Room();
        testRoom.setName("TestRoomName");
        entityManager.persist(testRoom);
        entityManager.flush();

        Room retrievedRoom = entityManager.find(Room.class, testRoom.getId());

        assertEquals(testRoom.getId(), retrievedRoom.getId());
        assertEquals(testRoom.getName(), retrievedRoom.getName());
    }
}
