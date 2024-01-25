package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends SoftDeleteRepository<User, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
}
