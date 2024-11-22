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
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EditRequestRepository extends JpaRepository<EditRequest, UUID> {
    @NotNull
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EditRequest e where e.id = ?1")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    Optional<EditRequest> findById(@NotNull UUID id);

    Optional<EditRequest> findFirstByStatusIsOrderByCreatedAt(EditRequestStatus status);

    @Query(
        """
        SELECT e FROM EditRequest e
        WHERE (
            CAST(:sourceRecordingId as uuid) IS NULL OR
            e.sourceRecording.id = :sourceRecordingId
        )
        ORDER BY e.modifiedAt DESC
        """
    )
    Page<EditRequest> searchAllBy(@Param("sourceRecordingId") UUID sourceRecordingId, Pageable pageable);

    @Query("select e from EditRequest e where e.id = ?1")
    Optional<EditRequest> findByIdNotLocked(@NotNull UUID id);
}
