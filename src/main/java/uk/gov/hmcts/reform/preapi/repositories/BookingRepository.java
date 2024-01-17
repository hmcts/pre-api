package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface BookingRepository extends SoftDeleteRepository<Booking, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);

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
                AND
                (
                    CAST(:scheduledForFrom as Timestamp) IS NULL OR
                    CAST(:scheduledForUntil as Timestamp) IS NULL OR
                    b.scheduledFor BETWEEN :scheduledForFrom AND :scheduledForUntil
                )
            )
            AND b.deletedAt IS NULL
        ORDER BY b.scheduledFor ASC
        """
    )
    Page<Booking> searchBookingsBy(
        @Param("caseId") UUID caseId,
        @Param("reference") String reference,
        @Param("scheduledForFrom") Timestamp scheduledForFrom,
        @Param("scheduledForUntil") Timestamp scheduledForUntil,
        Pageable pageable
    );
}
