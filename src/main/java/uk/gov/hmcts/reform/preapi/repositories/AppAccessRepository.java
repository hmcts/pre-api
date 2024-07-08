package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"PMD.MethodNamingConventions", "PMD.UseObjectForClearerAPI"})
public interface AppAccessRepository extends JpaRepository<AppAccess, UUID> {

    Optional<AppAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);

    List<AppAccess> findAllByUser_IdAndDeletedAtIsNotNull(UUID id);

    Optional<AppAccess> findByIdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);
}
