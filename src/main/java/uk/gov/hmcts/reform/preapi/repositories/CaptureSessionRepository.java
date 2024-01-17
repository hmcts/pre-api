package uk.gov.hmcts.reform.preapi.repositories;


import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface CaptureSessionRepository extends SoftDeleteRepository<CaptureSession, UUID> {
    Optional<CaptureSession> findByIdAndDeletedAtIsNull(UUID captureSessionId);

    int countAllByBooking_CaseId_IdAndStatus(UUID caseId, RecordingStatus status);

    List<CaptureSession> findAllByStatus(RecordingStatus status);

    List<CaptureSession> findAllByBookingAndDeletedAtIsNull(Booking booking);

    @Query("""
        update #{#entityName} e
        set e.deletedAt=CURRENT_TIMESTAMP
        where e.booking=:booking
        and e.deletedAt is null
        """
    )
    @Modifying
    @Transactional
    void deleteAllByBooking(Booking booking);
}
