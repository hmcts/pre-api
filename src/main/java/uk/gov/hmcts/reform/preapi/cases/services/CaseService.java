package uk.gov.hmcts.reform.preapi.cases.services;

import uk.gov.hmcts.reform.preapi.model.Case;

import java.util.UUID;


public interface CaseService {
    Case findById(UUID id);
}
