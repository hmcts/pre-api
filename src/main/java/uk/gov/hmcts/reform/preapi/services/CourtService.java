package uk.gov.hmcts.reform.preapi.services;


import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtEmailDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
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

        Set<Region> regions = createCourtDTO.getRegions()
            .stream()
            .map(regionRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());

        Optional<Court> court = courtRepository.findById(createCourtDTO.getId());
        Court courtEntity = court.orElse(new Court());
        courtEntity.setId(createCourtDTO.getId());
        courtEntity.setName(createCourtDTO.getName());
        courtEntity.setCourtType(createCourtDTO.getCourtType());
        courtEntity.setLocationCode(createCourtDTO.getLocationCode());
        courtEntity.setCounty(createCourtDTO.getCounty());
        courtEntity.setPostcode(createCourtDTO.getPostcode());
        courtEntity.setGroupEmail(createCourtDTO.getGroupEmail());
        courtEntity.setRegions(regions);

        boolean isUpdate = court.isPresent();
        courtRepository.save(courtEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public List<CourtEmailDTO> updateCourtEmails(MultipartFile inputFile) {
        List<CourtEmailDTO> courtList = parseCsv(inputFile);
        courtList.forEach(court -> {
            if (court.getName() == null) {
                return; // For blank lines in CSV file
            }
            Court courtInDb = courtRepository.findFirstByName(court.getName())
                .orElseThrow(() -> new NotFoundException("Court does not exist: " + court.getName()));
            courtInDb.setGroupEmail(court.getGroupEmail());

            courtRepository.save(courtInDb);
        });

        return courtRepository.findAllBy().stream().map(CourtEmailDTO::new).toList();
    }

    private List<CourtEmailDTO> parseCsv(MultipartFile file) {
        try {
            @Cleanup BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(),
                                                                                      StandardCharsets.UTF_8));

            return new CsvToBeanBuilder<CourtEmailDTO>(reader)
                .withType(CourtEmailDTO.class)
                .build()
                .parse();
        } catch (Exception e) {
            log.error("Error when reading CSV file: {} ", e.getMessage());
            throw new UnknownServerException("Uploaded CSV file incorrectly formatted", e);
        }
    }
}
