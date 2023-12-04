package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.model.Case;
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
    public Case findById(UUID id) {
        return caseRepository.findById(id).map(Case::new).orElse(null);
    }

    @Transactional
    public List<Case> searchBy(String reference, UUID courtId) {
        return caseRepository
            .searchCasesBy(reference, courtId)
            .stream()
            .map(Case::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public void create(Case caseModel) {
        var court = courtRepository.findById(caseModel.getCourtId());

        if (court.isEmpty()) {
            throw new NotFoundException("Court: " + caseModel.getCourtId());
        }

        if (caseRepository.findById(caseModel.getId()).isPresent()) {
            throw new ConflictException(caseModel.getId().toString());
        }

        var newCase = new uk.gov.hmcts.reform.preapi.entities.Case();
        newCase.setId(caseModel.getId());
        newCase.setCourt(court.get());
        newCase.setReference(caseModel.getReference());
        newCase.setTest(caseModel.isTest());
        caseRepository.save(newCase);
    }

    @Transactional
    public void deleteById(UUID id) {
        var foundCase = caseRepository.findById(id);
        if (foundCase.isEmpty()) {
            throw new NotFoundException("Case: " + id);
        }
        var caseEntity = foundCase.get();

        if (caseEntity.isDeleted()) {
            throw new NotFoundException("Case: " + id);
        }

        caseEntity.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        caseRepository.save(caseEntity);
    }
}
