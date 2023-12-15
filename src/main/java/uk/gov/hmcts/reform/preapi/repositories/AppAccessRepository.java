package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface AppAccessRepository extends JpaRepository<AppAccess, UUID> {
    Optional<AppAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID userId);

    @Query(
        """
        SELECT a FROM AppAccess a
        WHERE a.deletedAt IS NULL
        AND a.user.deletedAt IS NULL
        AND (:firstName IS NULL OR a.user.firstName ILIKE %:firstName%)
        AND (:lastName IS NULL OR a.user.lastName ILIKE %:lastName%)
        AND (:email IS NULL OR a.user.email ILIKE %:email%)
        AND (:organisation IS NULL OR a.user.organisation ILIKE %:organisation%)
        AND (CAST(:courtId as uuid) IS NULL OR a.court.id = :courtId)
        AND (CAST(:roleId as uuid) IS NULL OR a.role.id = :roleId)
        """
    )
    List<AppAccess> searchAllBy(
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("email") String email,
        @Param("organisation") String organisation,
        @Param("courtId") UUID courtId,
        @Param("roleId") UUID roleId
    );

}
