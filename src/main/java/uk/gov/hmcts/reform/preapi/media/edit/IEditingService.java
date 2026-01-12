package uk.gov.hmcts.reform.preapi.media.edit;

import uk.gov.hmcts.reform.preapi.entities.EditRequest;

import java.util.UUID;

@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface IEditingService {
    void performEdit(UUID newRecordingId, EditRequest request);
}
