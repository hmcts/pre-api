package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface RecordingRepository extends JpaRepository<Recording, UUID> {
    Optional<Recording> findByIdAndCaptureSession_Booking_Id(UUID recordingId, UUID bookingId);

    List<Recording> findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(UUID bookingId);
}
