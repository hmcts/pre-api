package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface CaptureSessionRepository extends JpaRepository<CaptureSession, UUID> {
    Optional<CaptureSession> findByIdAndDeletedAtIsNull(UUID captureSessionId);

    int countAllByBooking_CaseId_IdAndStatus(UUID caseId, RecordingStatus status);
}
