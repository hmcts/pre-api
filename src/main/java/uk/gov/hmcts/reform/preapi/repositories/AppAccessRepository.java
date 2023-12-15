package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface AppAccessRepository extends SoftDeleteRepository<AppAccess, UUID> {
    Optional<AppAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);
}
