package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"PMD.MethodNamingConventions", "PMD.UseObjectForClearerAPI"})
public interface AppAccessRepository extends SoftDeleteRepository<AppAccess, UUID> {

    Optional<AppAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);

    List<AppAccess> findAllByUser_EmailIgnoreCaseAndDeletedAtNullAndUser_DeletedAtNull(String email);

    Optional<AppAccess> findByIdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);
}
