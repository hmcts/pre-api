package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditCutInstructionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO.fromDTO;

@Slf4j
@Service
public class EditRequestCrudService {

    private final EditRequestRepository editRequestRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingService recordingService;
    private final EditCutInstructionsRepository editCutInstructionsRepository;

    @Autowired
    public EditRequestCrudService(final EditRequestRepository editRequestRepository,
                                  final RecordingRepository recordingRepository,
                                  final RecordingService recordingService,
                                  final EditCutInstructionsRepository editCutInstructionsRepository) {
        this.editRequestRepository = editRequestRepository;
        this.recordingRepository = recordingRepository;
        this.recordingService = recordingService;
        this.editCutInstructionsRepository = editCutInstructionsRepository;
    }

    @Transactional
    public EditRequestDTO findById(UUID id) {
        return editRequestRepository
            .findByIdNotLocked(id)
            .map(EditRequestDTO::new)
            .orElseThrow(() -> new NotFoundException("Edit Request: " + id));
    }

    @Transactional
    public Page<EditRequestDTO> findAll(@NotNull SearchEditRequests params, Pageable pageable) {
        return editRequestRepository
            .searchAllBy(params, pageable)
            .map(EditRequestDTO::new);
    }

    @Transactional
    public Optional<EditRequest> getNextPendingEditRequest() {
        return editRequestRepository.findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Transactional
    public void delete(EditRequestDTO dto) {
        Optional<EditRequest> req = editRequestRepository.findById(dto.getId());

        if (req.isEmpty()) {
            log.info("Attempt to delete non-existing edit request with id {}", dto.getId());
            return;
        }

        editRequestRepository.delete(req.get());
    }

    @Transactional
    public EditRequestDTO createOrUpsertDraftEditRequestInstructions(EditRequestDTO dto, User user) {
        Recording originalRecording = validateSourceRecording(dto.getSourceRecordingId());

        Optional<EditRequest> mostRecentEditRequest = editRequestRepository
            .findFirstBySourceRecordingIdIs(originalRecording.getId());

        if (mostRecentEditRequest.isEmpty()) {
            // Deliberately allow new (draft) edit request with empty instructions
            // However, edit requests cannot be *submitted* with empty instructions
            return createEditRequest(user, originalRecording, EditRequestDTO.fromDTO(dto.getEditInstructions()));
        }

        // A non-draft edit request exists; create a new one with previous instructions attached
        // Non-draft edit requests are read-only and should not be altered
        if (mostRecentEditRequest.get().getStatus() != EditRequestStatus.DRAFT) {
            return createEditRequest(user, originalRecording, mostRecentEditRequest.get().getEditCutInstructions());
        }

        // Draft edit request exists: delete all current instructions and replace with updated instructions
        // In practice these might be identical
        // We might prefer to do an actual upsert on these to preserve edit instruction creation time and createdBy info?
        editCutInstructionsRepository.deleteAll(mostRecentEditRequest.get().getEditCutInstructions());
        editCutInstructionsRepository.saveAll(fromDTO(dto.getEditInstructions()));

        return dto;
    }

    private EditRequestDTO createEditRequest(User user,
                                             Recording originalRecording,
                                             List<EditCutInstructions> cutInstructions) {
        if (originalRecording.getVersion() != 1) {
            throw new BadRequestException(format(
                "Can only perform edits on original recording (Version 1). "
                    + "Recording %s is version %d. Perhaps you need parent recording %s?",
                originalRecording.getId(),
                originalRecording.getVersion(),
                originalRecording.getParentRecording()
            ));
        }

        EditRequest newEditRequest = new EditRequest();
        newEditRequest.setId(UUID.randomUUID());
        newEditRequest.setCreatedBy(user);
        newEditRequest.setStatus(EditRequestStatus.DRAFT);
        newEditRequest.setSourceRecordingId(originalRecording.getId());
        newEditRequest.setOutputRecordingId(null); // This will be set by the processing service
        newEditRequest.setEditCutInstructions(cutInstructions);

        editRequestRepository.save(newEditRequest);

        return new EditRequestDTO(newEditRequest);
    }

    private @NotNull Recording validateSourceRecording(UUID sourceRecordingId) {
        recordingService.syncRecordingMetadataWithStorage(sourceRecordingId);

        Recording originalRecording = recordingRepository.findByIdAndDeletedAtIsNull(sourceRecordingId)
            .orElseThrow(() -> new NotFoundException("Source Recording: " + sourceRecordingId));

        if (originalRecording.getDuration() == null) {
            throw new ResourceInWrongStateException("Source Recording ("
                                                        + sourceRecordingId
                                                        + ") does not have a valid duration");
        }
        return originalRecording;
    }
}
