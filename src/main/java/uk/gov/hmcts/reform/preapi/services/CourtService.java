package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoomRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourtService {
    private final CourtRepository courtRepository;
    private final RegionRepository regionRepository;
    private final RoomRepository roomRepository;

    @Autowired
    public CourtService(CourtRepository courtRepository, RegionRepository regionRepository,
                        RoomRepository roomRepository) {
        this.courtRepository = courtRepository;
        this.regionRepository = regionRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public CourtDTO findById(UUID courtId) {
        return courtRepository
            .findById(courtId)
            .map(CourtDTO::new)
            .orElseThrow(
                () -> new NotFoundException("Court: " + courtId)
            );
    }

    @Transactional
    public Page<CourtDTO> findAllBy(
        CourtType courtType,
        String name,
        String locationCode,
        String regionName,
        Pageable pageable
    ) {
        return courtRepository
            .searchBy(courtType, name, locationCode, regionName, pageable)
            .map(CourtDTO::new);
    }

    @Transactional
    public UpsertResult upsert(CreateCourtDTO createCourtDTO) {
        createCourtDTO.getRegions().forEach(r -> {
            if (!regionRepository.existsById(r)) {
                throw new NotFoundException("Region: " + r);
            }
        });

        createCourtDTO.getRooms().forEach(r -> {
            if (!roomRepository.existsById(r)) {
                throw new NotFoundException("Room: " + r);
            }
        });

        var regions = createCourtDTO.getRegions()
            .stream()
            .map(regionRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());

        var rooms = createCourtDTO.getRooms()
            .stream()
            .map(roomRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());

        var court = courtRepository.findById(createCourtDTO.getId());
        var courtEntity = court.orElse(new Court());
        courtEntity.setId(createCourtDTO.getId());
        courtEntity.setName(createCourtDTO.getName());
        courtEntity.setCourtType(createCourtDTO.getCourtType());
        courtEntity.setLocationCode(createCourtDTO.getLocationCode());
        courtEntity.setRegions(regions);
        courtEntity.setRooms(rooms);

        var isUpdate = court.isPresent();
        courtRepository.save(courtEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }
}
