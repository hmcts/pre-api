package uk.gov.hmcts.reform.preapi.repositories;


import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"PMD.MethodNamingConventions", "checkstyle:LineLength"})
public interface RecordingRepository extends SoftDeleteRepository<Recording, UUID> {
    Optional<Recording> findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
        UUID recordingId
    );

    Optional<Recording> findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(CaptureSession captureSession, int version);

    @Query(
        """
        SELECT r FROM Recording r
        WHERE r.deletedAt IS NULL
        AND (:authorisedBookings IS NULL OR r.captureSession.booking.id IN :authorisedBookings)
        AND (CAST(:authCourtId as uuid) IS NULL OR r.captureSession.booking.caseId.court.id = :authCourtId)
        AND (
            CAST(:captureSessionId as uuid) IS NULL OR
            r.captureSession.id = :captureSessionId
        )
        AND (
            CAST(:parentRecordingId as uuid) IS NULL OR
            r.parentRecording.id = :parentRecordingId
        )
        AND (
            CAST(:participantId as uuid) IS NULL OR EXISTS (
                SELECT 1 FROM r.captureSession.booking.participants p
                WHERE p.id = :participantId
            )
        )
        AND (
            :caseReference IS NULL OR
            r.captureSession.booking.caseId.reference ILIKE %:caseReference%
        )
        AND (
            CAST(:scheduledForFrom as Timestamp) IS NULL OR
            CAST(:scheduledForUntil as Timestamp) IS NULL OR
            r.captureSession.booking.scheduledFor BETWEEN :scheduledForFrom AND :scheduledForUntil
        )
        AND (
            CAST(:courtId as uuid) IS NULL OR
            r.captureSession.booking.caseId.court.id = :courtId
        )
        AND (
            :participantName IS NULL OR EXISTS (
                SELECT 1 FROM r.captureSession.booking.participants p
                WHERE CONCAT(p.firstName, ' ', p.lastName) ILIKE %:participantName%
            )
        )
        """
    )
    Page<Recording> searchAllBy(
        @Param("captureSessionId") UUID captureSessionId,
        @Param("parentRecordingId") UUID parentRecordingId,
        @Param("participantId") UUID participantId,
        @Param("caseReference") String caseReference,
        @Param("scheduledForFrom") Timestamp scheduledForFrom,
        @Param("scheduledForUntil") Timestamp scheduledForUntil,
        @Param("courtId") UUID courtId,
        @Param("participantName") String participantName,
        @Param("authorisedBookings") List<UUID> authorisedBookings,
        @Param("authCourtId") UUID authCourtId,
        Pageable pageable
    );

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    List<Recording> findAllByParentRecordingIsNotNull();

    List<Recording> findAllByParentRecordingIsNull();

    @Query("""
        update #{#entityName} e
        set e.deletedAt=CURRENT_TIMESTAMP
        where e.captureSession=:captureSession
        and e.deletedAt is null
        """
    )
    @Modifying
    @Transactional
    void deleteAllByCaptureSession(CaptureSession captureSession);
}
