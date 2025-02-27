package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface CaptureSessionRepository extends JpaRepository<CaptureSession, UUID> {
    Optional<CaptureSession> findByIdAndDeletedAtIsNull(UUID captureSessionId);

    int countAllByBooking_CaseId_IdAndStatus(UUID caseId, RecordingStatus status);

    List<CaptureSession> findAllByStatus(RecordingStatus status);

    List<CaptureSession> findAllByFinishedAtIsAndDeletedAtIsNull(Timestamp finishedAt);

    List<CaptureSession> findAllByBookingAndDeletedAtIsNull(Booking booking);

    @Query(
        """
        SELECT c FROM CaptureSession c
        WHERE c.deletedAt IS NULL
        AND (:authorisedBookings IS NULL OR c.booking.id IN :authorisedBookings)
        AND (CAST(:authCourtId as uuid) IS NULL OR c.booking.caseId.court.id = :authCourtId)
        AND (:caseReference IS NULL OR c.booking.caseId.reference ILIKE %:caseReference%)
        AND (CAST(:bookingId as uuid) IS NULL OR c.booking.id = :bookingId)
        AND (CAST(:origin as text) IS NULL OR c.origin = :origin)
        AND (CAST(:recordingStatus as text) IS NULL OR c.status = :recordingStatus)
        AND (CAST(:courtId as uuid) IS NULL OR c.booking.caseId.court.id = :courtId)
        AND (
            CAST(:scheduledForFrom as Timestamp) IS NULL OR
            CAST(:scheduledForUntil as Timestamp) IS NULL OR
            CAST(FUNCTION('TIMEZONE', 'Europe/London', c.booking.scheduledFor) as Timestamp)
            BETWEEN :scheduledForFrom AND :scheduledForUntil
        )
        """
    )
    Page<CaptureSession> searchCaptureSessionsBy(
        @Param("caseReference") String caseReference,
        @Param("bookingId") UUID bookingId,
        @Param("origin") RecordingOrigin origin,
        @Param("recordingStatus") RecordingStatus recordingStatus,
        @Param("courtId") UUID courtId,
        @Param("scheduledForFrom") Timestamp scheduledForFrom,
        @Param("scheduledForUntil") Timestamp scheduledForUntil,
        @Param("authorisedBookings") List<UUID> authorisedBookings,
        @Param("authCourtId") UUID authCourtId,
        Pageable pageable
    );

    @Query("""
        SELECT cs FROM CaptureSession cs
        WHERE cs.deletedAt IS NULL
        AND cs.startedAt IS NOT NULL
        """
    )
    List<CaptureSession> reportConcurrentCaptureSessions();

    List<CaptureSession> findByDateAndDeletedAtIsNull(Timestamp deletedAt);
}
