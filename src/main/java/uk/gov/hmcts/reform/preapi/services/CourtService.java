package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourtService {
    private final CourtRepository courtRepository;
    @Autowired
    public CourtService(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
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
}
