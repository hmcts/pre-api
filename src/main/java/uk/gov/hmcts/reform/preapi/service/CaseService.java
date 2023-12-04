package uk.gov.hmcts.reform.preapi.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.model.Case;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.util.UUID;

@Service
public class CaseService {

    private final CaseRepository caseRepository;

    @Autowired
    CaseService(CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @Transactional
    public Case findById(UUID id) {
        return caseRepository
            .findById(id)
            .map(Case::new)
            .orElse(null);
    }
}
