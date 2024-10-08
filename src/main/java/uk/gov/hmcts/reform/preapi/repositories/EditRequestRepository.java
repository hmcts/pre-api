package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.persistence.LockModeType;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EditRequestRepository extends JpaRepository<EditRequest, UUID> {
    // Use following annotation on all methods you wish to block read/write operations
    // until the completion of the transaction:
    // @Lock(LockModeType.PESSIMISTIC_WRITE)

    @NotNull
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EditRequest e where e.id = ?1")
    Optional<EditRequest> findByIdWithLock(@NotNull UUID id);

    List<EditRequest> findAllByStatusOrderByCreatedAtDesc(EditRequestStatus status);
}
