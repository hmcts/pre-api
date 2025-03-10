package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"PMD.MethodNamingConventions", "PMD.UseObjectForClearerAPI"})
public interface AppAccessRepository extends JpaRepository<AppAccess, UUID> {

    ArrayList<AppAccess> findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);

    List<AppAccess> findAllByUser_IdAndDeletedAtIsNotNull(UUID id);

    @Query(
        """
        SELECT a FROM AppAccess a
        WHERE a.id = :userId
        AND a.deletedAt IS NULL
        AND a.active IS TRUE
        AND a.user.deletedAt IS NULL
        """
    )
    Optional<AppAccess> findByIdValidUser(@Param("userId") UUID userId);

    List<AppAccess> findAllByOrderByUser_LastNameAsc();
}
