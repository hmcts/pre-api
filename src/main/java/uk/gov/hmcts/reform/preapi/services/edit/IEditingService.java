package uk.gov.hmcts.reform.preapi.services.edit;

import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.util.UUID;

public interface IEditingService {
    void performEdit(UUID newRecordingId, EditRequest request);

    EditRequest prepareEditRequestToCreateOrUpdate(CreateEditRequestDTO createEditRequestDTO,
                                                   Recording sourceRecording,
                                                   EditRequest request);
}
