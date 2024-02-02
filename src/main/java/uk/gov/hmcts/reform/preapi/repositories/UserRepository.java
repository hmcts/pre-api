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
        LEFT JOIN a FROM AppAccess a
        LEFT JOIN p FROM PortalAccess p
        WHERE a.deletedAt IS NULL
        AND a.user.deletedAt IS NULL
        AND (:firstName IS NULL OR a.user.firstName ILIKE %:firstName%)
        AND (:lastName IS NULL OR a.user.lastName ILIKE %:lastName%)
        AND (:email IS NULL OR a.user.email ILIKE %:email%)
        AND (:organisation IS NULL OR a.user.organisation ILIKE %:organisation%)
        AND (CAST(:courtId as uuid) IS NULL OR a.court.id = :courtId)
        AND (CAST(:roleId as uuid) IS NULL OR a.role.id = :roleId)
        HAVING (:isPortalUser = false OR (:isPortalUser = true AND COUNT(p) > 0))
        HAVING (:isAppUser = false OR (:isAppUser = true AND COUNT(a) > 0))
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
