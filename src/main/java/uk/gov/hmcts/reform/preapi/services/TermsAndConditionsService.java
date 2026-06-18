package uk.gov.hmcts.reform.preapi.services;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TermsAndConditionsService {
    private final TermsAndConditionsRepository termsAndConditionsRepository;

    @Autowired
    public TermsAndConditionsService(TermsAndConditionsRepository termsAndConditionsRepository) {
        this.termsAndConditionsRepository = termsAndConditionsRepository;
    }

    public TermsAndConditionsDTO getLatestTermsAndConditionsByType(@NotNull TermsAndConditionsType type) {
        return termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(type)
            .map(TermsAndConditionsDTO::new)
            .orElseThrow(() -> new NotFoundException("Terms and conditions of type: " + type));
    }

    @Transactional
    public Set<TermsAndConditions> getAllLatestTermsAndConditions() {
        return Arrays.stream(TermsAndConditionsType.values())
            .map(type -> termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(type)
                .orElse(null))
            .collect(Collectors.toSet());
    }

}
