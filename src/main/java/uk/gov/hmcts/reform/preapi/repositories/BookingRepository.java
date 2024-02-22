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
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface BookingRepository extends SoftDeleteRepository<Booking, UUID> {
    boolean existsByIdAndDeletedAtIsNotNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<Booking> findByIdAndDeletedAtIsNull(UUID id);

    Page<Booking> findByCaseId_IdAndDeletedAtIsNull(UUID caseId, Pageable pageable);

    @Query(
        """
        SELECT b FROM Booking b
        INNER JOIN b.caseId
        WHERE
            (
                (
                    :reference IS NULL OR
                    b.caseId.reference ILIKE %:reference%
                )
                AND
                (
                    CAST(:caseId as uuid) IS NULL OR
                    b.caseId.id = :caseId
                )
                AND
                (
                    CAST(:courtId as uuid) IS NULL OR
                    b.caseId.court.id = :courtId
                )
                AND
                (
                    CAST(:scheduledForFrom as Timestamp) IS NULL OR
                    CAST(:scheduledForUntil as Timestamp) IS NULL OR
                    b.scheduledFor BETWEEN :scheduledForFrom AND :scheduledForUntil
                )
                AND (
                    CAST(:participantId as uuid) IS NULL OR EXISTS (
                        SELECT 1 FROM b.participants p
                        WHERE p.id = :participantId
                    )
                )
            )
            AND b.deletedAt IS NULL
            AND (
                :authorisedBookings IS NULL OR
                b.id IN :authorisedBookings
            )
            AND (CAST(:authCourtId as uuid) IS NULL OR
                b.caseId.court.id = :authCourtId
            )
            AND (
                :hasRecordings IS NULL
                OR (:hasRecordings = TRUE AND EXISTS (
                    SELECT 1 FROM Recording r
                    WHERE r.captureSession.booking = b
                ))
                OR (:hasRecordings = FALSE AND NOT EXISTS (
                    SELECT 1 FROM Recording r
                    WHERE r.captureSession.booking = b
                ))
            )
        ORDER BY b.scheduledFor ASC
        """
    )
    Page<Booking> searchBookingsBy(
        @Param("caseId") UUID caseId,
        @Param("reference") String reference,
        @Param("courtId") UUID courtId,
        @Param("scheduledForFrom") Timestamp scheduledForFrom,
        @Param("scheduledForUntil") Timestamp scheduledForUntil,
        @Param("participantId") UUID participantId,
        @Param("authorisedBookings") List<UUID> authorisedBookings,
        @Param("authCourtId") UUID authCourtId,
        @Param("hasRecordings") Boolean hasRecordings,
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
