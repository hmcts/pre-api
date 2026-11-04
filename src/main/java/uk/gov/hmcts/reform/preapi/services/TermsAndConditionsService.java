package uk.gov.hmcts.reform.preapi.services;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TermsAndConditionsService {
    private final TermsAndConditionsRepository termsAndConditionsRepository;

    @Value("${feature-flags.dynatrace-terms-and-conditions.cut-off-date}")
    private LocalDate cutOffDate;

    @Value("${feature-flags.dynatrace-terms-and-conditions.enabled}")
    private boolean isDynatraceAppTermsEnabled;


    @Autowired
    public TermsAndConditionsService(TermsAndConditionsRepository termsAndConditionsRepository) {
        this.termsAndConditionsRepository = termsAndConditionsRepository;
    }

    /**
     * Returns the latest terms for the given type.
     * For `PORTAL`, always returns the newest terms.
     * Temporarily - For `APP`, returns either the newest terms or the newest terms before the configured cutoff date,
     * depending on `feature-flags.dynatrace-terms-and-conditions.enabled` property.
     * @param type terms and conditions type (`APP` or `PORTAL`)
     * @return latest applicable terms and conditions
     */
    public TermsAndConditionsDTO getLatestTermsAndConditionsByType(@NotNull TermsAndConditionsType type) {
        boolean useLatestTerms = type == TermsAndConditionsType.PORTAL || isDynatraceAppTermsEnabled;
        Optional<TermsAndConditions> terms = useLatestTerms
            ? termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(type)
            : termsAndConditionsRepository.findFirstByTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
            type, Timestamp.valueOf(cutOffDate.atStartOfDay()));

        return terms.map(TermsAndConditionsDTO::new)


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
