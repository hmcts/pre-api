package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.repositories.RoomRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RoomService.class)
public class RoomServiceTest {
    private static Room roomEntity;

    @MockitoBean
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @BeforeAll
    static void setUp() {
        roomEntity = new Room();
        roomEntity.setId(UUID.randomUUID());
        roomEntity.setName("Example Room");
        roomEntity.setCourts(Set.of());
    }

    @DisplayName("Find all rooms and return a list of models")
    @Test
    void findAllRoomsSuccess() {
        when(roomRepository.searchAllBy(null)).thenReturn(List.of(roomEntity));

        var models = roomService.getAllRooms(null);

        assertThat(models.size()).isEqualTo(1);
        assertThat(models.getFirst().getId()).isEqualTo(roomEntity.getId());
        assertThat(models.getFirst().getName()).isEqualTo(roomEntity.getName());
    }
}
