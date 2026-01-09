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
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

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

    Optional<EditRequest> findFirstByStatusIsOrderByCreatedAt(EditRequestStatus status);

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
        SELECT (COUNT(e) > 0) from EditRequest e
        WHERE e.sourceRecording.captureSession.booking.caseId.id = :caseId
        AND (e.status != 'COMPLETE')
        """)
    boolean existsByCaseIdAndIsIncomplete(@Param("caseId") UUID caseId);
}
