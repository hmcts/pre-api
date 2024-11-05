package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TermsAndConditionsRepository extends JpaRepository<TermsAndConditions, UUID> {
    Optional<TermsAndConditions> findFirstByTypeOrderByCreatedAtDesc(TermsAndConditionsType type);
}
