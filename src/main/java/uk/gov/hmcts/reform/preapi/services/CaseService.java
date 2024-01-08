package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

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
    public Page<CaseDTO> searchBy(String reference, UUID courtId, Pageable pageable) {
        return caseRepository
            .searchCasesBy(reference, courtId, pageable)
            .map(CaseDTO::new);
    }

    @Transactional
    public UpsertResult upsert(CreateCaseDTO createCaseDTO) {
        var foundCase = caseRepository.findById(createCaseDTO.getId());

        if (foundCase.isPresent() && foundCase.get().isDeleted()) {
            throw new ResourceInDeletedStateException("CaseDTO", createCaseDTO.getId().toString());
        }

        var isUpdate = foundCase.isPresent();
        var court = courtRepository.findById(createCaseDTO.getCourtId()).orElse(null);

        if (!isUpdate && court == null) {
            throw new NotFoundException("Court: " + createCaseDTO.getCourtId());
        }

        var newCase = foundCase.orElse(new Case());
        newCase.setId(createCaseDTO.getId());
        newCase.setCourt(court);
        newCase.setReference(createCaseDTO.getReference());
        newCase.setTest(createCaseDTO.isTest());
        caseRepository.save(newCase);
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!caseRepository.existsByIdAndDeletedAtIsNull(id)) {
            throw new NotFoundException("CaseDTO: " + id);
        }
        caseRepository.deleteById(id);
    }
}
