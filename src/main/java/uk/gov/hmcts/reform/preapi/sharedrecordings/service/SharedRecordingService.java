package uk.gov.hmcts.reform.preapi.sharedrecordings.service;

import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface SharedRecordingService {
    List<Recording> findAllSharedWithUser(UUID user);

    Optional<Recording> findOneSharedWithUser(UUID recording, UUID user);
}
