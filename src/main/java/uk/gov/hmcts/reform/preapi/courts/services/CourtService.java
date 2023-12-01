package uk.gov.hmcts.reform.preapi.courts.services;

import uk.gov.hmcts.reform.preapi.entities.Court;

import java.util.UUID;

public interface CourtService {
    Court findById(UUID id);
}
