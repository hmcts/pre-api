package uk.gov.hmcts.reform.preapi.services.edit;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class EditRequestCrudService {

    private final EditRequestRepository editRequestRepository;
    private final IEditingService editingService;

    @Autowired
    public EditRequestCrudService(final EditRequestRepository editRequestRepository,
                                  final IEditingService editingService) {
        this.editRequestRepository = editRequestRepository;
        this.editingService = editingService;
    }

    public Optional<EditRequest> findByIdIfExists(UUID id) {
        return editRequestRepository
                .findByIdNotLocked(id);
    }

    public EditRequestDTO findById(UUID id) {
        return editRequestRepository
                .findByIdNotLocked(id)
                .map(EditRequestDTO::new)
                .orElseThrow(() -> new NotFoundException("Edit Request: " + id));
    }

    public Page<EditRequestDTO> findAll(final SearchEditRequests params, Pageable pageable) {
        return editRequestRepository.searchAllBy(params, pageable).map(EditRequestDTO::new);
    }

    public Optional<EditRequest> getNextPendingEditRequest() {
        return editRequestRepository.findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Transactional(noRollbackFor = Exception.class)
    public EditRequest updateEditRequestStatus(UUID id, EditRequestStatus status) {
        EditRequest request = editRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Edit Request: " + id));

        request.setStatus(status);
        switch (status) {
            case PROCESSING -> request.setStartedAt(Timestamp.from(Instant.now()));
            case ERROR, COMPLETE -> request.setFinishedAt(Timestamp.from(Instant.now()));
            default -> {
            }
        }
        editRequestRepository.save(request);
        return request;
    }

    @Transactional
    public void delete(CreateEditRequestDTO dto) {
        Optional<EditRequest> req = editRequestRepository.findById(dto.getId());

        if (req.isEmpty()) {
            log.info("Attempt to delete non-existing edit request with id {}", dto.getId());
            return;
        }

        editRequestRepository.delete(req.get());
    }

    @Transactional
    public @NotNull Pair<UpsertResult, EditRequest> upsert(CreateEditRequestDTO dto,
                                                           Recording sourceRecording, User user) {
        validateEditMode(dto);
        Optional<EditRequest> existingEditRequest = findByIdIfExists(dto.getId());
        boolean isUpdate = existingEditRequest.isPresent();
        UpsertResult emptyInstructionResult = handleEmptyInstructions(dto, existingEditRequest);
        if (emptyInstructionResult != null) {
            return Pair.of(emptyInstructionResult, existingEditRequest.orElse(new EditRequest()));
        }

        EditRequest request = editingService.prepareEditRequestToCreateOrUpdate(
                dto, sourceRecording,
                existingEditRequest.orElse(new EditRequest())
        );

        if (!isUpdate) {
            request.setCreatedBy(user);
            request.setCreatedAt(Timestamp.from(Instant.now()));
        }

        editRequestRepository.save(request);
        if (isUpdate) {
            return Pair.of(UpsertResult.UPDATED, request);
        }
        return Pair.of(UpsertResult.CREATED, request);
    }

    private void validateEditMode(CreateEditRequestDTO dto) {
        log.debug("Validating edit request {}", dto);
        if (dto.isForceReencode() && dto.getEditInstructions() != null && !dto.getEditInstructions().isEmpty()) {
            throw new BadRequestException(
                    "Invalid Instruction: Cannot request cuts and force reencode on the same edit request");
        }
    }

    private UpsertResult handleEmptyInstructions(CreateEditRequestDTO dto,
                                                 Optional<EditRequest> existingEditRequest) {
        if (dto.isForceReencode() || dto.getEditInstructions() != null && !dto.getEditInstructions().isEmpty()) {
            return null;
        }

        log.info(
                "Deleting edit request {} for source recording {} as edit instructions are empty",
                existingEditRequest
                        .orElseThrow(() -> new BadRequestException("Invalid Instruction: Cannot delete edit request "
                                + "instructions: does not exist"))
                        .getId(), dto.getSourceRecordingId()
        );
        delete(dto);
        return UpsertResult.UPDATED;
    }


}
