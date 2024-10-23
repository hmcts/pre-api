package uk.gov.hmcts.reform.preapi.services;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;

@Service
public class TermsAndConditionsService {
    private final TermsAndConditionsRepository termsAndConditionsRepository;

    @Autowired
    public TermsAndConditionsService(TermsAndConditionsRepository termsAndConditionsRepository) {
        this.termsAndConditionsRepository = termsAndConditionsRepository;
    }

    public TermsAndConditionsDTO getLatestTermsAndConditions(@NotNull TermsAndConditionsType type) {
        return termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(type)
            .map(TermsAndConditionsDTO::new)
            .orElseThrow(() -> new NotFoundException("Terms and conditions of type: " + type));
    }
}
