package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.RoomDTO;
import uk.gov.hmcts.reform.preapi.repositories.RoomRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    @Autowired
    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional
    public List<RoomDTO> getAllRooms() {
        return roomRepository
            .findAll()
            .stream()
            .map(RoomDTO::new)
            .collect(Collectors.toList());
    }
}
