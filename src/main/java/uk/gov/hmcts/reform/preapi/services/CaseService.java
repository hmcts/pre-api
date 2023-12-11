package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UpdateDeletedException;
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
        return caseRepository
            .findByIdAndDeletedAtIsNull(id)
            .map(CaseDTO::new)
            .orElseThrow(() -> new NotFoundException("Case: " + id));
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
    public UpsertResult upsert(CreateCaseDTO createCaseDTO) {
        var foundCase = caseRepository.findById(createCaseDTO.getId());
        var court = courtRepository.findById(createCaseDTO.getCourtId()).orElse(null);

        if (foundCase.isPresent() && foundCase.get().isDeleted()) {
            throw new UpdateDeletedException("Case: " + createCaseDTO.getId());
        }

        var isUpdate = foundCase.isPresent();

        if (!isUpdate && court == null) {
            throw new NotFoundException("Court: " + createCaseDTO.getCourtId());
        }

        var newCase = new Case();
        newCase.setId(createCaseDTO.getId());
        newCase.setCourt(court);
        newCase.setReference(createCaseDTO.getReference());
        newCase.setTest(createCaseDTO.isTest());
        caseRepository.save(newCase);
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
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
