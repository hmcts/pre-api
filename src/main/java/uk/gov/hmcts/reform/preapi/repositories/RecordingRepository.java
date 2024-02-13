package uk.gov.hmcts.reform.preapi.repositories;


import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;

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
        AND (:#{#searchParams.authorisedBookings} IS NULL OR r.captureSession.booking.id IN :#{#searchParams.authorisedBookings})
        AND (:#{#searchParams.authorisedCourt} IS NULL OR r.captureSession.booking.caseId.court.id = :#{#searchParams.authorisedCourt})
        AND (
            :#{#searchParams.captureSessionId} IS NULL OR
            r.captureSession.id = :#{#searchParams.captureSessionId}
        )
        AND (
            :#{#searchParams.parentRecordingId} IS NULL OR
            r.parentRecording.id = :#{#searchParams.parentRecordingId}
        )
        AND (
            :#{#searchParams.participantId} IS NULL OR EXISTS (
                SELECT 1 FROM r.captureSession.booking.participants p
                WHERE p.id = :#{#searchParams.participantId}
            )
        )
        AND (
            :#{#searchParams.caseReference} IS NULL OR
            r.captureSession.booking.caseId.reference ILIKE %:#{#searchParams.caseReference}%
        )
        AND (
            NULLIF(COALESCE(:#{#searchParams.scheduledForFrom}, 'NULL'), 'NULL') IS NULL OR
            NULLIF(COALESCE(:#{#searchParams.scheduledForUntil}, 'NULL'), 'NULL') IS NULL OR
            r.captureSession.booking.scheduledFor
            BETWEEN :#{#searchParams.scheduledForFrom}
            AND :#{#searchParams.scheduledForUntil}
        )
        AND (
            :#{#searchParams.courtId} IS NULL OR
            r.captureSession.booking.caseId.court.id = :#{#searchParams.courtId}
        )
        AND (
            :#{#searchParams.witnessName} IS NULL OR EXISTS (
                SELECT 1 FROM r.captureSession.booking.participants p
                WHERE CONCAT(p.firstName, ' ', p.lastName) ILIKE %:#{#searchParams.witnessName}%
                AND p.participantType = 'WITNESS'
                AND p.deletedAt IS NULL
            )
        )
        AND (
            :#{#searchParams.defendantName} IS NULL OR EXISTS (
                SELECT 1 FROM r.captureSession.booking.participants p
                WHERE CONCAT(p.firstName, ' ', p.lastName) ILIKE %:#{#searchParams.defendantName}%
                AND p.participantType = 'DEFENDANT'
                AND p.deletedAt IS NULL
            )
        )
        """
    )
    Page<Recording> searchAllBy(
        @Param("searchParams") SearchRecordings searchParams,
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
