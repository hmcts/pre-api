package uk.gov.hmcts.reform.preapi.services.edit;

import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;

import java.util.UUID;

public interface IEditingService {
    void performEdit(UUID newRecordingId, RecordingDTO recording, EditRequestDTO editRequestDTO);
}
