package uk.gov.hmcts.reform.preapi.cases.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class CaseServiceImpl implements CaseService {

    @Autowired
    private CaseRepository caseRepository;

    @Transactional
    @Override
    public Case findById(UUID id) {
        return caseRepository.findById(id).orElse(null);
    }

    @Transactional
    @Override
    public List<Case> findByReference(String reference) {
        return caseRepository.findByReferenceContainsIgnoreCase(reference);
    }

    @Transactional
    @Override
    public List<Case> findAll() {
        return caseRepository.findAll();
    }

    @Transactional
    @Override
    public Case save(Case caseEntity) {
        return caseRepository.save(caseEntity);
    }

    @Transactional
    @Override
    public void delete(Case caseEntity) {
        if (caseEntity.getDeletedAt() != null) {
            caseEntity.setDeletedAt(new Timestamp(System.currentTimeMillis()));
            caseRepository.save(caseEntity);
        }
    }
}
