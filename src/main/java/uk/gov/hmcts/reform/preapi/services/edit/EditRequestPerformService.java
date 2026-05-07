package uk.gov.hmcts.reform.preapi.services.edit;


import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.Optional;
import java.util.UUID;

import static uk.gov.hmcts.reform.preapi.media.edit.EditInstructions.fromJson;
import static uk.gov.hmcts.reform.preapi.utils.JsonUtils.toJson;

@Slf4j
@Service
public class EditRequestPerformService {
    private final IEditingService editingService;
    private final AssetGenerationService assetGenerationService;
    private final EditRequestCrudService editRequestCrudService;
    private final RecordingService recordingService;

    @Autowired
    public EditRequestPerformService(IEditingService editingService,
                                     AssetGenerationService assetGenerationService,
                                     EditRequestCrudService editRequestCrudService,
                                     RecordingService recordingService) {
        this.editingService = editingService;
        this.assetGenerationService = assetGenerationService;
        this.editRequestCrudService = editRequestCrudService;
        this.recordingService = recordingService;
    }

    public Optional<EditRequest> getNextPendingEditRequest() {
        return editRequestCrudService.getNextPendingEditRequest();
    }

    @Transactional(noRollbackFor = {Exception.class, RuntimeException.class}, propagation = Propagation.REQUIRES_NEW)
    public RecordingDTO performEdit(EditRequest request) throws InterruptedException {
        UUID newRecordingId = UUID.randomUUID();
        String filename;
        try {
            editingService.performEdit(newRecordingId, request);
            filename = assetGenerationService.generateAsset(newRecordingId, request);
        } catch (Exception e) {
            editRequestCrudService.updateEditRequestStatus(request.getId(), EditRequestStatus.ERROR);
            throw e;
        }

        editRequestCrudService.updateEditRequestStatus(request.getId(), EditRequestStatus.COMPLETE);

        CreateRecordingDTO createDto = createRecordingDto(newRecordingId, filename, request);
        recordingService.upsert(createDto);

        return recordingService.findById(newRecordingId);
    }

    @Transactional
    public EditRequest markAsProcessing(UUID editId) {
        log.info("Performing Edit Request: {}", editId);
        // retrieves locked edit request
        EditRequestDTO request = editRequestCrudService.findById(editId);
        EditRequestValidator.ensureStatusIsPendingBeforeProcessingEdit(request);
        return editRequestCrudService.updateEditRequestStatus(request.getId(), EditRequestStatus.PROCESSING);
    }

    private @NotNull CreateRecordingDTO createRecordingDto(UUID newRecordingId, String filename, EditRequest request) {
        UUID parentId = request.getSourceRecording().getParentRecording() == null
            ? request.getSourceRecording().getId()
            : request.getSourceRecording().getParentRecording().getId();

        // if edit on edit without original edits saved (legacy edit),
        //  then these edits will not align with the original timeline
        EditInstructionDump dump = new EditInstructionDump(request.getId(), fromJson(request.getEditInstruction()));

        return new CreateRecordingDTO(
            newRecordingId,
            parentId,
            request.getSourceRecording().getCaptureSession().getId(),
            recordingService.getNextVersionNumber(parentId),
            filename,
            // duration is auto-generated
            null,
            toJson(dump)
        );
    }

    private record EditInstructionDump(UUID editRequestId, EditInstructions editInstructions) {
    }
}
