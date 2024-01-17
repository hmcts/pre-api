package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface BookingRepository extends SoftDeleteRepository<Booking, UUID> {
    boolean existsByIdAndDeletedAtIsNotNull(UUID id);

    Optional<Booking> findByIdAndDeletedAtIsNull(UUID id);

    Page<Booking> findByCaseId_IdAndDeletedAtIsNull(UUID caseId, Pageable pageable);

    @Query(
        """
        SELECT b FROM Booking b
        INNER JOIN b.caseId
        WHERE
            (
                (
                    CAST(:reference as text) IS NULL OR
                    LOWER(CAST(b.caseId.reference as text)) LIKE CONCAT('%', LOWER(CAST(:reference as text)), '%')
                )
                AND
                (
                    CAST(:caseId as org.hibernate.type.UUIDCharType) IS NULL OR
                    b.caseId.id = :caseId
                )
            )
            AND b.deletedAt IS NULL
        ORDER BY b.scheduledFor ASC
        """
    )
    Page<Booking> searchBookingsBy(
        @Param("caseId") UUID caseId,
        @Param("reference") String reference,
        Pageable pageable
    );

    List<Booking> findAllByCaseIdAndDeletedAtIsNull(Case caseId);

    @Query("""
        update #{#entityName} e
        set e.deletedAt=CURRENT_TIMESTAMP
        where e.caseId=:caseEntity
        and e.deletedAt is null
        """
    )
    @Modifying
    @Transactional
    void deleteAllByCaseId(Case caseEntity);
}
