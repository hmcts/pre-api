package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private final CaseRepository caseRepository;

    private final CourtRepository courtRepository;

    @Autowired
    public CaseService(CaseRepository caseRepository, CourtRepository courtRepository) {
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
    }

    @Transactional
    public CaseDTO findById(UUID id) {
        return caseRepository.findById(id).map(CaseDTO::new).orElse(null);
    }

    @Transactional
    public List<CaseDTO> searchBy(String reference, UUID courtId) {
        return caseRepository
            .searchCasesBy(reference, courtId)
            .stream()
            .map(CaseDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public void create(CaseDTO caseDTOModel) {
        var court = courtRepository.findById(caseDTOModel.getCourtId());

        if (court.isEmpty()) {
            throw new NotFoundException("Court: " + caseDTOModel.getCourtId());
        }

        if (caseRepository.findById(caseDTOModel.getId()).isPresent()) {
            throw new ConflictException(caseDTOModel.getId().toString());
        }

        var newCase = new uk.gov.hmcts.reform.preapi.entities.Case();
        newCase.setId(caseDTOModel.getId());
        newCase.setCourt(court.get());
        newCase.setReference(caseDTOModel.getReference());
        newCase.setTest(caseDTOModel.isTest());
        caseRepository.save(newCase);
    }

    @Transactional
    public void deleteById(UUID id) {
        var foundCase = caseRepository.findById(id);
        if (foundCase.isEmpty()) {
            throw new NotFoundException("CaseDTO: " + id);
        }
        var caseEntity = foundCase.get();

        if (caseEntity.isDeleted()) {
            throw new NotFoundException("CaseDTO: " + id);
        }

        caseEntity.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        caseRepository.save(caseEntity);
    }
}
