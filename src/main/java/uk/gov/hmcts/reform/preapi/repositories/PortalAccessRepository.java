package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface PortalAccessRepository extends JpaRepository<PortalAccess, UUID> {
    Optional<PortalAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID id);

    Optional<PortalAccess> findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(UUID id, AccessStatus status);

    Optional<PortalAccess> findByUser_EmailIgnoreCaseAndDeletedAtNullAndUser_DeletedAtNull(String email);

    Optional<PortalAccess> findByIdAndDeletedAtIsNull(UUID id);

    @Query(
        """
        SELECT pa FROM PortalAccess pa
        WHERE pa.deletedAt IS NULL
        AND (CAST(:status as text) IS NULL OR pa.status = :status)
        AND (:firstName IS NULL OR pa.user.firstName ILIKE %:firstName%)
        AND (:lastName IS NULL OR pa.user.lastName ILIKE %:lastName%)
        AND (:email IS NULL OR pa.user.email ILIKE %:email%)
        AND (:organisation IS NULL OR pa.user.organisation ILIKE %:organisation%)
        """
    )
    Page<PortalAccess> findAllBy(
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("email") String email,
        @Param("organisation") String organisation,
        @Param("status") AccessStatus status,
        Pageable pageable
    );

    List<PortalAccess> findAllByUser_IdAndDeletedAtIsNotNull(UUID id);

    @Query(
        """
        SELECT p FROM PortalAccess p
        WHERE p.id = :userId
        AND p.deletedAt IS NULL
        AND p.user.deletedAt IS NULL
        AND p.registeredAt IS NOT NULL
        AND p.invitedAt IS NOT NULL
        AND p.status = 'ACTIVE'
        """
    )
    Optional<PortalAccess> findByIdValidUser(@Param("userId") UUID userId);

}
