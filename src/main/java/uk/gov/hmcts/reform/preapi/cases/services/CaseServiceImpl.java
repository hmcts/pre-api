package uk.gov.hmcts.reform.preapi.cases.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.models.Case;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseServiceImpl implements CaseService {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Transactional
    @Override
    public Case findById(UUID id) {
        return caseRepository.findById(id).map(Case::new).orElse(null);
    }

    @Transactional
    @Override
    public List<Case> searchBy(String reference, UUID courtId) {
        return caseRepository
            .searchCasesBy(reference, courtId)
            .stream()
            .map(Case::new)
            .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void create(Case caseModel) {
        var court = courtRepository.findById(caseModel.getCourtId());

        if (court.isEmpty()) {
            // throw
            return;
        }

        uk.gov.hmcts.reform.preapi.entities.Case newCase = new uk.gov.hmcts.reform.preapi.entities.Case();
        newCase.setId(caseModel.getId());
        newCase.setCourt(court.get());
        newCase.setReference(caseModel.getReference());
        newCase.setTest(caseModel.isTest());
        caseRepository.save(newCase);
    }

    @Transactional
    @Override
    public void deleteById(UUID id) {
        var foundCase = caseRepository.findById(id);
        if (foundCase.isEmpty()) {
            // todo throw not found
            return;
        }
        var caseEntity = foundCase.get();

        if (caseEntity.isDeleted()) {
            // todo throw not found
        }

        caseEntity.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        caseRepository.save(caseEntity);
    }
}
