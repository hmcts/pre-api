package uk.gov.hmcts.reform.preapi.recordings.services;

import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingService {
    List<Recording> get();

    Optional<Recording> get(UUID id);
}
