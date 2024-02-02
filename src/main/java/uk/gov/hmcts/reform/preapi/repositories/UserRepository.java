package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends SoftDeleteRepository<User, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    @Query(
        """
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        AND (:firstName IS NULL OR u.firstName ILIKE %:firstName%)
        AND (:lastName IS NULL OR u.lastName ILIKE %:lastName%)
        AND (:email IS NULL OR u.email ILIKE %:email%)
        AND (:organisation IS NULL OR u.organisation ILIKE %:organisation%)
        AND (CAST(:courtId as uuid) IS NULL OR EXISTS (SELECT 1 FROM u.appAccess aa WHERE aa.court.id = :courtId))
        AND (CAST(:roleId as uuid) IS NULL OR EXISTS (SELECT 1 FROM u.appAccess aa WHERE aa.role.id = :roleId))
        AND (:isPortalUser = false OR EXISTS (SELECT 1 FROM u.portalAccess pa WHERE pa.user = u))
        AND (:isAppUser = false OR EXISTS (SELECT 1 FROM u.appAccess aa WHERE aa.user = u))
        """
    )
    Page<User> searchAllBy(
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("email") String email,
        @Param("organisation") String organisation,
        @Param("courtId") UUID courtId,
        @Param("roleId") UUID roleId,
        @Param("isPortalUser") Boolean isPortalUser,
        @Param("isAppUser") Boolean isAppUser,
        Pageable pageable
    );
}
