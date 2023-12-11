package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends SoftDeleteRepository<Booking, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNotNull(UUID id);

    Optional<Booking> findByIdAndDeletedAtIsNull(UUID id);
}
