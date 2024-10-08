package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted;

import java.util.UUID;

@Repository
public interface UserTermsAcceptedRepository extends JpaRepository<UserTermsAccepted, UUID> {
}
