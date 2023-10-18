package uk.gov.hmcts.reform.preapi.sharedrecordings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.SharedRecording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharedRecordingRepository extends JpaRepository<SharedRecording, UUID> {
    List<SharedRecording> findByUser_Id(UUID userId);

    Optional<SharedRecording> findByUser_IdAndRecording_id(UUID userId, UUID recordingId);
}
