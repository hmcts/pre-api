package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.ShareRecording;

import java.util.UUID;

@Repository
public interface ShareRecordingRepository extends JpaRepository<ShareRecording, UUID> {
    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
