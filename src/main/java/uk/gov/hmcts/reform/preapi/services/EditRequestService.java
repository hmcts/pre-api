package uk.gov.hmcts.reform.preapi.services;

import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.edit.AssetGenerationService;
import uk.gov.hmcts.reform.preapi.services.edit.IEditingService;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizerUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static uk.gov.hmcts.reform.preapi.media.edit.EditInstructions.fromJson;
import static uk.gov.hmcts.reform.preapi.utils.JsonUtils.toJson;

@Slf4j
@Service
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.GodClass"})
public class EditRequestService {
    private final EditRequestRepository editRequestRepository;
    private final RecordingRepository recordingRepository;
    private final IEditingService editingService;
    private final RecordingService recordingService;
    private final AssetGenerationService assetGenerationService;
    private final EditNotificationService editNotificationService;
    private final boolean hideReencodedRecordings;

    private static final String ROLE_SUPER_USER = "ROLE_SUPER_USER";

    @Autowired
    public EditRequestService(final EditRequestRepository editRequestRepository,
                              final RecordingRepository recordingRepository,
                              final IEditingService editingService,
                              final RecordingService recordingService,
                              final AssetGenerationService assetGenerationService,
                              final EditNotificationService editNotificationService,
                              @Value("${feature-flags.hide-reencoded-recordings:true}")
                              final boolean hideReencodedRecordings) {
        this.editRequestRepository = editRequestRepository;
        this.recordingRepository = recordingRepository;
        this.editingService = editingService;
        this.recordingService = recordingService;
        this.assetGenerationService = assetGenerationService;
        this.editNotificationService = editNotificationService;
        this.hideReencodedRecordings = hideReencodedRecordings;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasEditRequestAccess(authentication, #id)")
    public EditRequestDTO findById(UUID id) {
        boolean includeReencodedRecordings = canViewReencodedRecordings();
        return editRequestRepository
            .findByIdNotLocked(id)
            .map(editRequest -> new EditRequestDTO(editRequest, true, includeReencodedRecordings))
            .orElseThrow(() -> new NotFoundException("Edit Request: " + id));
    }

    @Transactional
    public Page<EditRequestDTO> findAll(@NotNull SearchEditRequests params, Pageable pageable) {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        boolean includeReencodedRecordings = canViewReencodedRecordings(auth);
        params.setAuthorisedBookings(auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings());
        params.setAuthorisedCourt(auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId());

        return editRequestRepository
            .searchAllBy(params, pageable)
            .map(editRequest -> new EditRequestDTO(editRequest, true, includeReencodedRecordings));
    }

    @Transactional
    public Optional<EditRequest> getNextPendingEditRequest(boolean reencodeOnly) {
        if (reencodeOnly) {
            return editRequestRepository.findFirstPendingReencodeEditRequest();
        }

        return editRequestRepository.findFirstPendingRegularEditRequest();
    }

    @Transactional(readOnly = true)
    public Set<UUID> findRecordingIdsAlreadyQueuedOrCompletedForReencode(Set<UUID> sourceRecordingIds) {
        if (sourceRecordingIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> reencodeRecordingIds = new HashSet<>(
            recordingRepository.findRecordingIdsWithCompletedReencode(sourceRecordingIds)
        );
        reencodeRecordingIds.addAll(editRequestRepository.findSourceRecordingIdsWithForceReencodeRequests(
            sourceRecordingIds
        ));
        return reencodeRecordingIds;
    }

    @Transactional
    public void updateEditRequestStatus(UUID id, EditRequestStatus status) {
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
    }

    @Transactional(noRollbackFor = Exception.class)
    public EditRequest markAsProcessing(UUID editId) throws InterruptedException {
        log.info("Performing Edit Request: {}", editId);
        // retrieves locked edit request
        EditRequest request = editRequestRepository.findById(editId)
            .orElseThrow(() -> new NotFoundException("Edit Request: " + editId));

        if (request.getStatus() != EditRequestStatus.PENDING) {
            throw new ResourceInWrongStateException(
                EditRequest.class.getSimpleName(),
                request.getId().toString(),
                request.getStatus().toString(),
                EditRequestStatus.PENDING.toString()
            );
        }
        updateEditRequestStatus(request.getId(), EditRequestStatus.PROCESSING);
        return request;
    }

    @Transactional(noRollbackFor = {Exception.class, RuntimeException.class}, propagation = Propagation.REQUIRES_NEW)
    public RecordingDTO performEdit(EditRequest request) throws InterruptedException {
        UUID newRecordingId = UUID.randomUUID();
        String filename;
        try {
            editingService.performEdit(newRecordingId, request);
            filename = assetGenerationService.generateAsset(newRecordingId, request);
        } catch (Exception e) {
            updateEditRequestStatus(request.getId(), EditRequestStatus.ERROR);
            throw e;
        }

        updateEditRequestStatus(request.getId(), EditRequestStatus.COMPLETE);

        CreateRecordingDTO createDto = createRecordingDto(newRecordingId, filename, request);
        recordingService.upsert(createDto);

        return recordingService.findById(newRecordingId);
    }

    @Transactional
    public @NotNull CreateRecordingDTO createRecordingDto(UUID newRecordingId, String filename, EditRequest request) {
        UUID parentId = request.getSourceRecording().getParentRecording() == null
            ? request.getSourceRecording().getId()
            : request.getSourceRecording().getParentRecording().getId();

        CreateRecordingDTO createDto = new CreateRecordingDTO();
        createDto.setId(newRecordingId);
        createDto.setParentRecordingId(parentId);
        // if edit on edit without original edits saved (legacy edit),
        //  then these edits will not align with the original timeline
        EditInstructionDump dump = new EditInstructionDump(request.getId(), fromJson(request.getEditInstruction()));
        createDto.setEditInstructions(toJson(dump));
        createDto.setVersion(recordingService.getNextVersionNumber(parentId));
        createDto.setCaptureSessionId(request.getSourceRecording().getCaptureSession().getId());
        createDto.setFilename(filename);
        // duration is auto-generated
        return createDto;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #dto)")
    public void delete(CreateEditRequestDTO dto) {
        Optional<EditRequest> req = editRequestRepository.findById(dto.getId());

        if (req.isEmpty()) {
            log.info("Attempt to delete non-existing edit request with id {}", dto.getId());
            return;
        }

        editRequestRepository.delete(req.get());
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #dto)")
    public UpsertResult upsert(CreateEditRequestDTO dto) {
        validateEditMode(dto);
        Recording sourceRecording = getSourceRecording(dto.getSourceRecordingId());
        Optional<EditRequest> existingEditRequest = editRequestRepository.findById(dto.getId());
        boolean isUpdate = existingEditRequest.isPresent();
        UpsertResult emptyInstructionResult = handleEmptyInstructions(dto, existingEditRequest, isUpdate);
        if (emptyInstructionResult != null) {
            return emptyInstructionResult;
        }

        EditRequest request = editingService.prepareEditRequestToCreateOrUpdate(dto, sourceRecording,
                                                             existingEditRequest.orElse(new EditRequest()));

        setCreatedByForNewRequest(request, isUpdate);
        notifyOnUpdatedRequest(dto, request, isUpdate);
        editRequestRepository.save(request);
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #sourceRecordingId)")
    public EditRequestDTO upsert(UUID sourceRecordingId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getContentType() == null || !file.getContentType().equals("text/csv")) {
            throw new BadRequestException("File type is not supported: expected text/csv");
        }

        // temporary code for create edit request with csv endpoint
        UUID id = UUID.randomUUID();
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(id);
        dto.setSourceRecordingId(sourceRecordingId);
        dto.setEditInstructions(parseCsv(file));
        dto.setStatus(EditRequestStatus.PENDING);
        dto.getEditInstructions().forEach(editInstruction -> {
            if (!InputSanitizerUtils.isValid(editInstruction.getReason(), false)) {
                throw new BadRequestException("Edit instruction reason potentially contains malicious code: "
                                                  + editInstruction.getReason());
            }
        });

        upsert(dto);

        return editRequestRepository.findById(id)
            .map(EditRequestDTO::new)
            .orElseThrow(() -> new UnknownServerException("Edit Request failed to create"));
    }

    private void validateEditMode(CreateEditRequestDTO dto) {
        if (dto.isForceReencode() && dto.getEditInstructions() != null && !dto.getEditInstructions().isEmpty()) {
            throw new BadRequestException(
                "Invalid Instruction: Cannot request cuts and force reencode on the same edit request");
        }
    }

    private Recording getSourceRecording(UUID sourceRecordingId) {
        recordingService.syncRecordingMetadataWithStorage(sourceRecordingId);

        Recording sourceRecording = recordingRepository.findByIdAndDeletedAtIsNull(sourceRecordingId)
            .orElseThrow(() -> new NotFoundException("Source Recording: " + sourceRecordingId));

        if (sourceRecording.getDuration() == null) {
            throw new ResourceInWrongStateException("Source Recording ("
                                                        + sourceRecordingId
                                                        + ") does not have a valid duration");
        }

        return sourceRecording;
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

    private void setCreatedByForNewRequest(EditRequest request, boolean isUpdate) {
        if (isUpdate) {
            return;
        }

        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        User user = auth.isAppUser() ? auth.getAppAccess().getUser() : auth.getPortalAccess().getUser();
        request.setCreatedBy(user);
    }

    private void notifyOnUpdatedRequest(CreateEditRequestDTO dto, EditRequest request, boolean isUpdate) {
        if (!isUpdate) {
            return;
        }

        if (dto.getStatus() == EditRequestStatus.SUBMITTED) {
            editNotificationService.onEditRequestSubmitted(request);
            return;
        }

        editNotificationService.onEditRequestRejected(request);
    }

    private boolean canViewReencodedRecordings() {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        return canViewReencodedRecordings(auth);
    }

    private boolean canViewReencodedRecordings(UserAuthentication auth) {
        return !hideReencodedRecordings || auth != null && auth.hasRole(ROLE_SUPER_USER);
    }

    private List<EditCutInstructionDTO> parseCsv(MultipartFile file) {
        try {
            @Cleanup BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8
            ));
            return new CsvToBeanBuilder<EditCutInstructionDTO>(reader)
                .withType(EditCutInstructionDTO.class)
                .build()
                .parse();
        } catch (Exception e) {
            log.error("Error when reading CSV file: {} ", e.getMessage());
            throw new UnknownServerException("Uploaded CSV file incorrectly formatted", e);
        }
    }

    private record EditInstructionDump(UUID editRequestId, EditInstructions editInstructions) {
    }
}
