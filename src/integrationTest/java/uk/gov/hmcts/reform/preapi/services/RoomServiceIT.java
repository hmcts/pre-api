package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.Set;

class RoomServiceIT extends IntegrationTestBase {
    @Autowired
    private RoomService roomService;

    private Court court1;
    private Court court2;
    private Room room1;
    private Room room2;

    @BeforeEach
    void setUp() {
        room1 = HelperFactory.createRoom("ROOM-01", Set.of());
        entityManager.persist(room1);
        room2 = HelperFactory.createRoom("ROOM-02", Set.of());
        entityManager.persist(room2);

        court1 = HelperFactory.createCourt(CourtType.CROWN, "Court 1", "Court 1");
        court1.setRooms(Set.of(room1));
        entityManager.persist(court1);
        court2 = HelperFactory.createCourt(CourtType.CROWN, "Court 2", "Court 2");
        court2.setRooms(Set.of(room2));
        entityManager.persist(court2);
    }

    @Transactional
    @Test
    void getAllRooms() {
        var rooms = roomService.getAllRooms(null);

        Assertions.assertEquals(2, rooms.size());
        Assertions.assertTrue(rooms.stream().anyMatch(r -> room1.getId().equals(r.getId())));
        Assertions.assertTrue(rooms.stream().anyMatch(r -> room2.getId().equals(r.getId())));
    }

    @Transactional
    @Test
    void getAllRoomsForCourt() {
        var rooms = roomService.getAllRooms(court1.getId());

        Assertions.assertEquals(1, rooms.size());
        Assertions.assertTrue(rooms.stream().anyMatch(r -> room1.getId().equals(r.getId())));
        Assertions.assertFalse(rooms.stream().anyMatch(r -> room2.getId().equals(r.getId())));
    }
}
