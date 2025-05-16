package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.EncodeJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncodeJobRepository extends JpaRepository<EncodeJob, UUID> {
    List<EncodeJob> findAllByDeletedAtIsNull();

    Optional<EncodeJob> findByIdAndDeletedAtIsNull(UUID id);
}
