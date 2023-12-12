package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoomRepository;

import java.util.List;
import java.util.Locale;
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
    public List<CourtDTO> findAllBy(CourtType courtType, String name, String locationCode, String regionName) {
        return courtRepository.searchBy(courtType, name, locationCode)
            .stream()
            .filter(
                court -> regionName == null
                    || court.getRegions()
                    .stream()
                    .map(Region::getName)
                    .anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(regionName.toLowerCase(Locale.ROOT)))
            )
            .map(CourtDTO::new)
            .collect(Collectors.toList());
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

        var courtEntity = new Court();
        courtEntity.setName(createCourtDTO.getName());
        courtEntity.setCourtType(createCourtDTO.getCourtType());
        courtEntity.setLocationCode(createCourtDTO.getLocationCode());
        // TODO waiting for db migration
        //        courtEntity.setRegions(regions);
        //        courtEntity.setRooms(rooms);

        var isUpdate = courtRepository.existsById(createCourtDTO.getId());
        courtEntity.setId(createCourtDTO.getId());
        courtRepository.save(courtEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }
}
