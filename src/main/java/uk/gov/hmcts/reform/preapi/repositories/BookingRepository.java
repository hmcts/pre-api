package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    boolean existsByIdAndDeletedAtIsNotNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<Booking> findByIdAndDeletedAtIsNull(UUID id);

    Page<Booking> findByCaseId_IdAndDeletedAtIsNull(UUID caseId, Pageable pageable);

    @Query(
        """
        SELECT b FROM Booking b
        LEFT JOIN b.captureSessions cs
        INNER JOIN b.caseId
        WHERE
            (
                (
                    :reference IS NULL OR
                    b.caseId.reference ILIKE %:reference%
                )
                AND
                 (
                     :includeDeleted = TRUE OR
                     b.deletedAt IS NULL
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
                    CAST(FUNCTION('TIMEZONE', 'Europe/London', b.scheduledFor) as Timestamp)
                    BETWEEN :scheduledForFrom AND :scheduledForUntil
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
            AND (:statuses IS NULL OR EXISTS (
                SELECT 1 FROM b.captureSessions AS cs
                WHERE cs.status in :statuses
            ))
            AND (:notStatuses IS NULL OR NOT EXISTS (
                SELECT 1 FROM b.captureSessions AS cs
                WHERE cs.status in :notStatuses
            ))
            AND (
                :hasRecordings IS NULL
                OR (:hasRecordings = TRUE AND EXISTS (
                    SELECT 1 FROM CaptureSession c
                    WHERE c.booking.id = b.id
                    AND c.status = 'RECORDING_AVAILABLE'
                ))
                OR (:hasRecordings = FALSE AND NOT EXISTS (
                    SELECT 1 FROM CaptureSession c
                    WHERE c.booking.id = b.id
                    AND c.status = 'RECORDING_AVAILABLE'
                ))
            )
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
        @Param("includeDeleted") Boolean includeDeleted,
        @Param("statuses") List<RecordingStatus> statuses,
        @Param("notStatuses") List<RecordingStatus> notStatuses,
        Pageable pageable
    );

    List<Booking> findAllByCaseIdAndDeletedAtIsNull(Case caseId);
}
