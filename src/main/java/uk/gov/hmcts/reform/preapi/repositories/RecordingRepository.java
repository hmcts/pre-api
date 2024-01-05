package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings("PMD.MethodNamingConventions")
public interface RecordingRepository extends JpaRepository<Recording, UUID> {
    Optional<Recording> findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
        UUID recordingId
    );

    @Query(
        """
        SELECT r FROM Recording r
        WHERE r.deletedAt IS NULL
        AND (
            CAST(:captureSessionId as uuid) IS NULL OR
            r.captureSession.id = :captureSessionId
        )
        AND (
            CAST(:parentRecordingId as uuid) IS NULL OR
            r.parentRecording.id = :parentRecordingId
        )
        """
    )
    Page<Recording> searchAllBy(
        @Param("captureSessionId") UUID captureSessionId,
        @Param("parentRecordingId") UUID parentRecordingId,
        Pageable pageable
    );

    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
