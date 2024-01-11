package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchUsers;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"PMD.MethodNamingConventions", "PMD.UseObjectForClearerAPI"})
public interface AppAccessRepository extends SoftDeleteRepository<AppAccess, UUID> {
    Optional<AppAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);

    @Query(
        """
        SELECT a FROM AppAccess a
        WHERE a.deletedAt IS NULL
        AND a.user.deletedAt IS NULL
        AND (:#{#params.firstName} IS NULL OR a.user.firstName ILIKE %:#{#params.firstName}%)
        AND (:#{#params.lastName} IS NULL OR a.user.lastName ILIKE %:#{#params.lastName}%)
        AND (:#{#params.email} IS NULL OR a.user.email ILIKE %:#{#params.email}%)
        AND (:#{#params.organisation} IS NULL OR a.user.organisation ILIKE %:#{#params.organisation}%)
        AND (:#{#params.courtId} IS NULL OR a.court.id = :#{#params.courtId})
        AND (:#{#params.roleId} IS NULL OR a.role.id = :#{#params.roleId})
        AND (:#{#params.active} IS NULL OR a.active = :#{#params.active})
        """
    )
    Page<AppAccess> searchAllBy(@Param("params") SearchUsers params, Pageable pageable);

}
