package uk.gov.hmcts.reform.preapi.helpers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

@Service
public class DatabaseHelper {

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private CaseRepository caseRepository;

    public void clearDatabase() {
        caseRepository.deleteAll();
        courtRepository.deleteAll();
    }
}
