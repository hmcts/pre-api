package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface RecordingRepository extends JpaRepository<Recording, UUID> {
    Optional<Recording> findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
        UUID recordingId
    );

    Optional<Recording> findByIdAndDeletedAtIsNull(UUID id);

    @Query(
        """
        SELECT r FROM Recording r
        WHERE (:includeDeleted = TRUE OR r.deletedAt IS NULL)
        AND (:#{#searchParams.authorisedBookings} IS NULL OR r.captureSession.booking.id IN :#{#searchParams.authorisedBookings})
        AND (:#{#searchParams.authorisedCourt} IS NULL OR r.captureSession.booking.caseId.court.id = :#{#searchParams.authorisedCourt})
        AND (
            :#{#searchParams.id} IS NULL OR
            CAST(r.id AS text) ILIKE %:#{#searchParams.id}%
        )
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
            NULLIF(COALESCE(:#{#searchParams.startedAtFrom}, 'NULL'), 'NULL') IS NULL OR
            NULLIF(COALESCE(:#{#searchParams.startedAtUntil}, 'NULL'), 'NULL') IS NULL OR
            r.captureSession.startedAt
            BETWEEN :#{#searchParams.startedAtFrom}
            AND :#{#searchParams.startedAtUntil}
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
        @Param("includeDeleted") boolean includeDeleted,
        Pageable pageable
    );

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    List<Recording> findAllByParentRecordingIsNotNull();

    List<Recording> findAllByParentRecordingIsNull();

    boolean existsByCaptureSessionAndDeletedAtIsNull(CaptureSession captureSession);

    @Query("""
        SELECT c, COUNT(c)
        FROM Recording r
        LEFT JOIN CaptureSession cs ON r.captureSession.id=cs.id
        LEFT JOIN Booking b ON cs.booking.id=b.id
        LEFT JOIN Case c ON b.caseId.id=c.id
        WHERE r.version = 1
        AND r.deletedAt IS NULL
        GROUP BY c
        ORDER BY count(c) DESC
        """
    )
    List<Object[]> countRecordingsPerCase();

    int countByParentRecording_Id(UUID id);

    @Query("""
        SELECT cs, r, b, u
        FROM CaptureSession cs
        INNER JOIN Recording r ON r.captureSession.id=cs.id
        INNER JOIN Booking b ON cs.booking.id=b.id
        LEFT JOIN User u ON u.id=cs.finishedByUser.id
        WHERE r.parentRecording IS NULL
        AND cs.status = 'RECORDING_AVAILABLE'
        """
    )
    List<Recording> findAllCompletedCaptureSessionsWithRecordings();
}
