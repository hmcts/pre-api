package uk.gov.hmcts.reform.preapi.cases.services;

import uk.gov.hmcts.reform.preapi.models.Case;

import java.util.List;
import java.util.UUID;


public interface CaseService {
    Case findById(UUID id);

    List<Case> searchBy(String reference, UUID courtId);

    void create(Case caseEntity);

    void deleteById(UUID id);
}
