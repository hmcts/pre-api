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
import uk.gov.hmcts.reform.preapi.services.EditNotificationService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class EditRequestCrudService {

    private final EditRequestRepository editRequestRepository;
    private final IEditingService editingService;
    private final EditNotificationService editNotificationService;

    @Autowired
    public EditRequestCrudService(final EditRequestRepository editRequestRepository,
                                  final IEditingService editingService,
                                  final EditNotificationService editNotificationService) {
        this.editRequestRepository = editRequestRepository;
        this.editingService = editingService;
        this.editNotificationService = editNotificationService;
    }

    public Optional<EditRequest> findByIdIfExists(UUID id) {
        return editRequestRepository
            .findByIdNotLocked(id);
    }

    public EditRequestDTO findById(UUID id) {
        return findById(id, true);
    }

    public EditRequestDTO findById(UUID id, boolean includeReencodedRecordings) {
        return editRequestRepository
            .findByIdNotLocked(id)
            .map(editRequest ->
                     new EditRequestDTO(editRequest, true, includeReencodedRecordings))
            .orElseThrow(() -> new NotFoundException("Edit Request: " + id));
    }

    public Page<EditRequestDTO> findAll(final SearchEditRequests params, Pageable pageable) {
        return findAll(params, pageable, true);
    }

    public Page<EditRequestDTO> findAll(
        final SearchEditRequests params,
        Pageable pageable,
        boolean includeReencodedRecordings
    ) {
        return editRequestRepository
            .searchAllBy(params, pageable)
            .map(editRequest -> new EditRequestDTO(editRequest, true, includeReencodedRecordings));
    }

    public Optional<EditRequest> getNextPendingEditRequest(boolean reencodeOnly) {
        if (reencodeOnly) {
            return editRequestRepository.findFirstPendingReencodeEditRequest();
        }

        return editRequestRepository.findFirstPendingRegularEditRequest();
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

        editNotificationService.editRequestStatusWasUpdated(request);

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
        EditRequestValidator.validateEditMode(dto);
        Optional<EditRequest> existingEditRequest = findByIdIfExists(dto.getId());
        boolean isUpdate = existingEditRequest.isPresent();
        UpsertResult emptyInstructionResult = handleEmptyInstructions(dto, existingEditRequest, isUpdate);
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

        boolean editStatusWasUpdated = !isUpdate || !existingEditRequest.get().getStatus().equals(dto.getStatus());
        if (editStatusWasUpdated) {
            editNotificationService.editRequestStatusWasUpdated(request);
        }

        if (isUpdate) {
            return Pair.of(UpsertResult.UPDATED, request);
        }
        return Pair.of(UpsertResult.CREATED, request);
    }

    @Transactional
    public Set<UUID> findRecordingIdsWithForceReencodeRequests(Set<UUID> sourceRecordingIds) {
        if (sourceRecordingIds.isEmpty()) {
            return Set.of();
        }

        return editRequestRepository.findSourceRecordingIdsWithForceReencodeRequests(sourceRecordingIds);
    }
  
    private UpsertResult handleEmptyInstructions(CreateEditRequestDTO dto,
                                                 Optional<EditRequest> existingEditRequest,
                                                 boolean isUpdate) {
        if (dto.isForceReencode() || dto.getEditInstructions() != null && !dto.getEditInstructions().isEmpty()) {
            return null;
        }

        if (!isUpdate) {
            throw new BadRequestException("Invalid Instruction: Cannot create an edit request with empty"
                                              + " instructions");
        }

        log.info(
            "Deleting edit request {} for source recording {} as edit instructions are empty",
            existingEditRequest.orElseThrow().getId(), dto.getSourceRecordingId()
        );
        delete(dto);
        return UpsertResult.UPDATED;
    }


}
