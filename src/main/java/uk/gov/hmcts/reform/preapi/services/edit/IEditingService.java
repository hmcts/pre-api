package uk.gov.hmcts.reform.preapi.services.edit;

import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;

import java.util.UUID;

public interface IEditingService {
    void performEdit(UUID newRecordingId, RecordingDTO recording);
}
