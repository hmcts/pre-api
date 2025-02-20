package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {
    List<Audit> findBySourceAndFunctionalAreaAndActivity(AuditLogSource source, String functionalArea, String activity);

    @Query(
        """
        SELECT a FROM Audit a
        WHERE a.activity != 'Recording Playback ended'
        AND CAST(FUNCTION('jsonb_extract_path_text', a.auditDetails, 'description') as text) ILIKE '%playback%'
        """
    )
    List<Audit> findAllAccessAttempts();

    List<Audit> findByTableRecordId(UUID tableRecordId);

    @Query(
        """
        SELECT a FROM Audit a
        WHERE (CAST(:after as Timestamp) IS NULL
            OR CAST(FUNCTION('TIMEZONE', 'Europe/London', a.createdAt) as Timestamp) >= :after)
        AND (CAST(:before as Timestamp) IS NULL
            OR CAST(FUNCTION('TIMEZONE', 'Europe/London', a.createdAt) as Timestamp) <= :before)
        AND (:functionalArea IS NULL OR a.functionalArea ILIKE %:functionalArea%)
        AND (CAST(:source as text) IS NULL OR a.source = :source)
        AND (:userName IS NULL
            OR EXISTS (
                SELECT 1 FROM AppAccess aa
                WHERE aa.id = a.createdBy
                AND CONCAT(aa.user.firstName, ' ', aa.user.lastName) ILIKE %:userName%)
            OR EXISTS (
                SELECT 1 FROM PortalAccess pa
                WHERE pa.id = a.createdBy
                AND CONCAT(pa.user.firstName, ' ', pa.user.lastName) ILIKE %:userName%))
        AND ((CAST(:courtId as uuid) IS NULL AND :caseReference IS NULL)
            OR EXISTS (
                SELECT 1 FROM Court court
                WHERE :caseReference IS NULL
                AND court.id = a.tableRecordId
                AND (CAST(:courtId as uuid) IS NULL OR court.id = :courtId))
            OR EXISTS (
                SELECT 1 FROM Case c
                WHERE c.id = a.tableRecordId
                AND (CAST(:courtId as uuid) IS NULL OR c.court.id = :courtId)
                AND (:caseReference IS NULL OR c.reference ILIKE %:caseReference%))
            OR EXISTS (
                SELECT 1 FROM Booking b
                WHERE b.id = a.tableRecordId
                AND (CAST(:courtId as uuid) IS NULL OR b.caseId.court.id = :courtId)
                AND (:caseReference IS NULL OR b.caseId.reference ILIKE %:caseReference%))
            OR EXISTS (
                SELECT 1 FROM CaptureSession cs
                WHERE cs.id = a.tableRecordId
                AND (CAST(:courtId as uuid) IS NULL OR cs.booking.caseId.court.id = :courtId)
                AND (:caseReference IS NULL OR cs.booking.caseId.reference ILIKE %:caseReference%))
            OR EXISTS (
                SELECT 1 FROM Recording r
                WHERE r.id = a.tableRecordId
                AND (CAST(:courtId as uuid) IS NULL OR r.captureSession.booking.caseId.court.id = :courtId)
                AND (:caseReference IS NULL OR r.captureSession.booking.caseId.reference ILIKE %:caseReference%))
            OR EXISTS (
                SELECT 1 FROM Participant p
                WHERE p.id = a.tableRecordId
                AND (CAST(:courtId as uuid) IS NULL OR p.caseId.court.id = :courtId)
                AND (:caseReference IS NULL OR p.caseId.reference ILIKE %:caseReference%)))
        ORDER BY a.createdAt DESC
        """
    )
    Page<Audit> searchAll(
        @Param("after") Timestamp after,
        @Param("before") Timestamp before,
        @Param("functionalArea") String functionalArea,
        @Param("source") AuditLogSource source,
        @Param("userName") String userName,
        @Param("courtId") UUID courtId,
        @Param("caseReference") String caseReference,
        Pageable pageable
    );
}
