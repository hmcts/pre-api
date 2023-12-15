package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends SoftDeleteRepository<Booking, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNotNull(UUID id);

    Optional<Booking> findByIdAndDeletedAtIsNull(UUID id);

    List<Booking> findByCaseId_IdAndDeletedAtIsNull(UUID caseId);

    @Query(
        """
        SELECT b FROM Booking b
        INNER JOIN b.caseId
        WHERE
            (
                CAST(:reference as text) IS NULL OR
                LOWER(CAST(b.caseId.reference as text)) LIKE CONCAT('%', LOWER(CAST(:reference as text)), '%')
            )
            AND b.deletedAt IS NULL
        ORDER BY b.scheduledFor ASC
        """
    )
    List<Booking> searchBookingsBy(
        @Param("reference") String reference
    );
}
