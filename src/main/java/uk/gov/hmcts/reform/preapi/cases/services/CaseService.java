package uk.gov.hmcts.reform.preapi.cases.services;

import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.List;
import java.util.UUID;


public interface CaseService {
    Case findById(UUID id);

    List<Case> searchBy(String reference, UUID courtId);

    Case save(Case caseEntity);

    void delete(Case caseEntity);
}
