package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.enums.ReencodeJobStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordingReencodeJobRepository extends JpaRepository<RecordingReencodeJob, UUID> {

    @NotNull
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from RecordingReencodeJob j where j.id = ?1")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    Optional<RecordingReencodeJob> findById(@NotNull UUID id);

    Optional<RecordingReencodeJob> findFirstByStatusOrderByCreatedAt(ReencodeJobStatus status);

    boolean existsByRecordingIdAndStatusIn(UUID recordingId, Collection<ReencodeJobStatus> statuses);
}
