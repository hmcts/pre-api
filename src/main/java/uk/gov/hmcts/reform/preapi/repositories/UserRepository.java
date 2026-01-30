package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    @Query(
        """
        SELECT u FROM User u
        WHERE (:includeDeleted = TRUE OR u.deletedAt IS NULL)
        AND (:isAppActive IS NULL OR EXISTS(
            SELECT 1 FROM u.appAccess aa
            WHERE aa.active = :isAppActive
            AND (CAST(:courtId as uuid) IS NULL OR aa.court.id = :courtId)
            AND aa.deletedAt IS NULL
        ))
        AND (
            CASE
                WHEN :name IS NULL AND :email IS NULL THEN 1
                WHEN :name IS NULL AND :email IS NOT NULL AND (
                    u.email ILIKE %:email% OR u.alternativeEmail ILIKE %:email%
                ) THEN 1
                WHEN :name IS NOT NULL AND CONCAT(u.firstName, ' ', u.lastName) ILIKE %:name% AND :email IS NULL THEN 1
                WHEN :name IS NOT NULL AND :email IS NOT NULL AND (
                    CONCAT(u.firstName, ' ', u.lastName) ILIKE %:name%
                    OR u.email ILIKE %:email%
                    OR u.alternativeEmail ILIKE %:email%
                ) THEN 1
                ELSE 0
            END = 1
        )
        AND (:organisation IS NULL OR u.organisation ILIKE %:organisation%)
        AND (CAST(:courtId as uuid) IS NULL OR EXISTS (SELECT 1 FROM u.appAccess aa WHERE aa.court.id = :courtId))
        AND (CAST(:roleId as uuid) IS NULL OR EXISTS (SELECT 1 FROM u.appAccess aa WHERE aa.role.id = :roleId))
        AND (
            :isPortalUser = false
            OR EXISTS (
                SELECT 1 FROM u.portalAccess pa
                WHERE pa.user = u AND pa.deletedAt IS NULL
                AND pa.status != 'INACTIVE'
            )
        )
        AND (:isAppUser = false OR EXISTS (SELECT 1 FROM u.appAccess aa WHERE aa.user = u AND aa.deletedAt IS NULL))
        """
    )
    Page<User> searchAllBy(
        @Param("name") String name,
        @Param("email") String email,
        @Param("organisation") String organisation,
        @Param("courtId") UUID courtId,
        @Param("roleId") UUID roleId,
        @Param("isPortalUser") Boolean isPortalUser,
        @Param("isAppUser") Boolean isAppUser,
        boolean includeDeleted,
        @Param("isAppActive") Boolean isAppActive,
        Pageable pageable
    );

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.portalAccess pa
        LEFT JOIN FETCH u.appAccess aa
        WHERE LOWER(u.email) = LOWER(:email)
        AND u.deletedAt IS NULL""")
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNullWithPortalAccess(@Param("email") String email);

    Optional<User> findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(String alternativeEmail);

    @Query("""
        SELECT u FROM User u
        WHERE (LOWER(u.email) = LOWER(:email) OR LOWER(u.alternativeEmail) = LOWER(:email))
        AND u.deletedAt IS NULL""")
    Optional<User> findByEmailOrAlternativeEmailIgnoreCaseAndDeletedAtIsNull(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"appAccess", "portalAccess"})
    @Query("""
        SELECT u FROM User u
        WHERE (u.email ILIKE '%.cjsm.net' AND u.alternativeEmail IS NOT NULL)
        AND EXISTS (
            SELECT 1 FROM u.portalAccess pa
            WHERE pa.user = u AND pa.deletedAt IS NULL
            AND pa.status != 'INACTIVE'
        )
        AND u.deletedAt IS NULL
        """)
    Page<User> findPortalUsersWithCjsmEmail(Pageable pageable);
}
