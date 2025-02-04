package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RoomTest extends IntegrationTestBase {

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
