package uk.gov.hmcts.reform.preapi.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourtService {
    private final CourtRepository courtRepository;
    private final RegionRepository regionRepository;

    @Autowired
    public CourtService(CourtRepository courtRepository, RegionRepository regionRepository) {
        this.courtRepository = courtRepository;
        this.regionRepository = regionRepository;
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

        var regions = createCourtDTO.getRegions()
            .stream()
            .map(regionRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());

        var court = courtRepository.findById(createCourtDTO.getId());
        var courtEntity = court.orElse(new Court());
        courtEntity.setId(createCourtDTO.getId());
        courtEntity.setName(createCourtDTO.getName());
        courtEntity.setCourtType(createCourtDTO.getCourtType());
        courtEntity.setLocationCode(createCourtDTO.getLocationCode());
        courtEntity.setCounty(createCourtDTO.getCounty());
        courtEntity.setPostcode(createCourtDTO.getPostcode());
        courtEntity.setGroupEmail(createCourtDTO.getGroupEmail());
        courtEntity.setRegions(regions);

        var isUpdate = court.isPresent();
        courtRepository.save(courtEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }
}
