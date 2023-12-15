package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;

import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface PortalAccessRepository extends SoftDeleteRepository<PortalAccess, UUID> {
    Optional<PortalAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID id);
}
