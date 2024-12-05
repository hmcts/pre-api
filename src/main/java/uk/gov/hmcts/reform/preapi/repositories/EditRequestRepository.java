package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EditRequestRepository extends JpaRepository<EditRequest, UUID> {
    @NotNull
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EditRequest e where e.id = ?1")
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    Optional<EditRequest> findById(@NotNull UUID id);

    List<EditRequest> findAllByStatusIsOrderByCreatedAt(EditRequestStatus status);
}
