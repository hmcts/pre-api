package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EditRequestRepository extends JpaRepository<EditRequest, UUID> {
    @NotNull
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EditRequest e where e.id = ?1")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    Optional<EditRequest> findById(@NotNull UUID id);

    @Query(value = """
        SELECT * FROM edit_requests e
        WHERE e.status = 'PENDING'
        AND COALESCE(e.edit_instruction ->> 'forceReencode', 'false') = 'false'
        ORDER BY e.created_at
        LIMIT 1
        """, nativeQuery = true)
    Optional<EditRequest> findFirstPendingRegularEditRequest();

    @Query(value = """
        SELECT * FROM edit_requests e
        WHERE e.status = 'PENDING'
        AND e.edit_instruction ->> 'forceReencode' = 'true'
        ORDER BY e.created_at
        LIMIT 1
        """, nativeQuery = true)
    Optional<EditRequest> findFirstPendingReencodeEditRequest();

    @Query(
        """
        SELECT e FROM EditRequest e
        WHERE (:#{#params.sourceRecordingId} IS NULL OR
            e.sourceRecording.id = :#{#params.sourceRecordingId})
        AND (:#{#params.authorisedBookings} IS NULL
            OR e.sourceRecording.captureSession.booking.id IN :#{#params.authorisedBookings})
        AND (:#{#params.authorisedCourt} IS NULL
            OR e.sourceRecording.captureSession.booking.caseId.court.id = :#{#params.authorisedCourt})
        AND (NULLIF(COALESCE(:#{#params.lastModifiedAfter}, 'NULL'), 'NULL') IS NULL
            OR e.modifiedAt >= :#{#params.lastModifiedAfter})
        AND (NULLIF(COALESCE(:#{#params.lastModifiedBefore}, 'NULL'), 'NULL') IS NULL
            OR e.modifiedAt <= :#{#params.lastModifiedBefore})
        """
    )
    Page<EditRequest> searchAllBy(
        @Param("params") SearchEditRequests params,
        Pageable pageable
    );

    @Query("select e from EditRequest e where e.id = ?1")
    Optional<EditRequest> findByIdNotLocked(@NotNull UUID id);

    @Query("""
        SELECT e FROM EditRequest e
        WHERE e.sourceRecording.id IN :sourceRecordingIds
        """)
    List<EditRequest> findAllBySourceRecordingIdIn(@Param("sourceRecordingIds") Collection<UUID> sourceRecordingIds);

    @Query("""
        SELECT (COUNT(e) > 0) from EditRequest e
        WHERE e.sourceRecording.captureSession.booking.caseId.id = :caseId
        AND (e.status != 'COMPLETE')
        """)
    boolean existsByCaseIdAndIsIncomplete(@Param("caseId") UUID caseId);
}
