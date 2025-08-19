package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EditRequestService {
    private final EditRequestRepository editRequestRepository;
    private final RecordingRepository recordingRepository;
    private final FfmpegService ffmpegService;
    private final RecordingService recordingService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    public EditRequestService(final EditRequestRepository editRequestRepository,
                              final RecordingRepository recordingRepository,
                              final FfmpegService ffmpegService,
                              final RecordingService recordingService,
                              final AzureIngestStorageService azureIngestStorageService,
                              final AzureFinalStorageService azureFinalStorageService,
                              final MediaServiceBroker mediaServiceBroker) {
        this.editRequestRepository = editRequestRepository;
        this.recordingRepository = recordingRepository;
        this.ffmpegService = ffmpegService;
        this.recordingService = recordingService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.mediaServiceBroker = mediaServiceBroker;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasEditRequestAccess(authentication, #id)")
    public EditRequestDTO findById(UUID id) {
        return editRequestRepository
            .findByIdNotLocked(id)
            .map(EditRequestDTO::new)
            .orElseThrow(() -> new NotFoundException("Edit Request: " + id));
    }

    @Transactional
    public Page<EditRequestDTO> findAll(SearchEditRequests params, Pageable pageable) {
        UserAuthentication auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        params.setAuthorisedBookings(auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings());
        params.setAuthorisedCourt(auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId());

        return editRequestRepository
            .searchAllBy(params, pageable)
            .map(EditRequestDTO::new);
    }

    @Transactional
    public Optional<EditRequest> getNextPendingEditRequest() {
        return editRequestRepository.findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
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
        request.setStartedAt(Timestamp.from(Instant.now()));
        request.setStatus(EditRequestStatus.PROCESSING);
        editRequestRepository.saveAndFlush(request);
        return request;
    }

    @Transactional(noRollbackFor = {Exception.class, RuntimeException.class}, propagation = Propagation.REQUIRES_NEW)
    public RecordingDTO performEdit(EditRequest request) throws InterruptedException {
        UUID newRecordingId = UUID.randomUUID();
        String filename;
        try {
            ffmpegService.performEdit(newRecordingId, request);
            filename = generateAsset(newRecordingId, request);
        } catch (Exception e) {
            request.setFinishedAt(Timestamp.from(Instant.now()));
            request.setStatus(EditRequestStatus.ERROR);
            editRequestRepository.saveAndFlush(request);
            throw e;
        }

        request.setFinishedAt(Timestamp.from(Instant.now()));
        request.setStatus(EditRequestStatus.COMPLETE);
        editRequestRepository.saveAndFlush(request);

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
        createDto.setEditInstructions(request.getEditInstruction());
        createDto.setVersion(recordingService.getNextVersionNumber(parentId));
        createDto.setCaptureSessionId(request.getSourceRecording().getCaptureSession().getId());
        createDto.setFilename(filename);
        // duration is auto-generated
        return createDto;
    }

    @Transactional
    public String generateAsset(UUID newRecordingId, EditRequest request) throws InterruptedException {
        String sourceContainer = newRecordingId + "-input";
        if (!azureIngestStorageService.doesContainerExist(sourceContainer)) {
            throw new NotFoundException("Source Container (" + sourceContainer + ") does not exist");
        }
        // throws 404 when doesn't exist
        azureIngestStorageService.getMp4FileName(sourceContainer);
        String assetName = newRecordingId.toString().replace("-", "");

        azureFinalStorageService.createContainerIfNotExists(newRecordingId.toString());

        GenerateAssetDTO generateAssetDto = GenerateAssetDTO.builder()
            .sourceContainer(sourceContainer)
            .destinationContainer(newRecordingId)
            .tempAsset(assetName)
            .finalAsset(assetName + "_output")
            .parentRecordingId(request.getSourceRecording().getId())
            .description("Edit of " + request.getSourceRecording().getId().toString().replace("-", ""))
            .build();

        GenerateAssetResponseDTO result = mediaServiceBroker.getEnabledMediaService()
            .importAsset(generateAssetDto, false);

        if (!result.getJobStatus().equals(JobState.FINISHED.toString())) {
            throw new UnknownServerException("Failed to generate asset for edit request: "
                                                 + request.getSourceRecording().getId()
                                                 + ", new recording: "
                                                 + newRecordingId);
        }
        return azureFinalStorageService.getMp4FileName(newRecordingId.toString());
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #dto)")
    public UpsertResult upsert(CreateEditRequestDTO dto) {
        recordingService.syncRecordingMetadataWithStorage(dto.getSourceRecordingId());

        Recording sourceRecording = recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId())
            .orElseThrow(() -> new NotFoundException("Source Recording: " + dto.getSourceRecordingId()));

        if (sourceRecording.getDuration() == null) {
            throw new ResourceInWrongStateException("Source Recording ("
                                                        + dto.getSourceRecordingId()
                                                        + ") does not have a valid duration");
        }

        boolean isOriginalRecordingEdit = sourceRecording.getParentRecording() == null;

        EditInstructions prevInstructions = null;
        boolean isInstructionCombination = !isOriginalRecordingEdit
            && sourceRecording.getEditInstruction() != null
            && !sourceRecording.getEditInstruction().isEmpty()
            && (prevInstructions = EditInstructions.tryFromJson(sourceRecording.getEditInstruction())) != null
            && prevInstructions.getFfmpegInstructions() != null
            && !prevInstructions.getFfmpegInstructions().isEmpty()
            && prevInstructions.getRequestedInstructions() != null
            && !prevInstructions.getRequestedInstructions().isEmpty();

        Optional<EditRequest> req = editRequestRepository.findById(dto.getId());
        EditRequest request = req.orElse(new EditRequest());

        request.setId(dto.getId());
        request.setSourceRecording(!isInstructionCombination
                                       ? sourceRecording
                                       : sourceRecording.getParentRecording());
        request.setStatus(dto.getStatus());

        List<EditCutInstructionDTO> requestedEdits = isInstructionCombination
            ? combineCutsOnOriginalTimeline(prevInstructions, dto.getEditInstructions())
            : dto.getEditInstructions();

        List<FfmpegEditInstructionDTO> editInstructions = invertInstructions(
            requestedEdits,
            isInstructionCombination ? request.getSourceRecording() : sourceRecording);

        request.setEditInstruction(toJson(new EditInstructions(requestedEdits, editInstructions)));

        boolean isUpdate = req.isPresent();
        if (!isUpdate) {
            User user = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication())
                .getAppAccess().getUser();

            request.setCreatedBy(user);
        }

        editRequestRepository.save(request);
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #sourceRecordingId)")
    public EditRequestDTO upsert(UUID sourceRecordingId, MultipartFile file) {
        // temporary code for create edit request with csv endpoint
        UUID id = UUID.randomUUID();
        upsert(CreateEditRequestDTO.builder()
                   .id(id)
                   .sourceRecordingId(sourceRecordingId)
                   .editInstructions(parseCsv(file))
                   .status(EditRequestStatus.PENDING)
                   .build());

        return editRequestRepository.findById(id)
            .map(EditRequestDTO::new)
            .orElseThrow(() -> new UnknownServerException("Edit Request failed to create"));
    }

    private List<EditCutInstructionDTO> parseCsv(MultipartFile file) {
        try {
            @Cleanup BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
            return new CsvToBeanBuilder<EditCutInstructionDTO>(reader)
                .withType(EditCutInstructionDTO.class)
                .build()
                .parse();
        } catch (Exception e) {
            log.error("Error when reading CSV file: {} ", e.getMessage());
            throw new UnknownServerException("Uploaded CSV file incorrectly formatted");
        }
    }

    protected List<FfmpegEditInstructionDTO> invertInstructions(final List<EditCutInstructionDTO> instructions,
                                                                final Recording recording) {
        long recordingDuration = recording.getDuration().toSeconds();
        if (instructions.size() == 1) {
            EditCutInstructionDTO i = instructions.getFirst();
            if (i.getStart() == 0 && i.getEnd() == recordingDuration) {
                throw new BadRequestException("Invalid Instruction: Cannot cut an entire recording: Start("
                                                  + i.getStart()
                                                  + "), End("
                                                  + i.getEnd()
                                                  + "), Recording Duration("
                                                  + recordingDuration
                                                  + ")");
            }
        }

        instructions.sort(Comparator.comparing(EditCutInstructionDTO::getStart)
                              .thenComparing(EditCutInstructionDTO::getEnd));

        for (int i = 1; i < instructions.size(); i++) {
            EditCutInstructionDTO prev = instructions.get(i - 1);
            EditCutInstructionDTO curr = instructions.get(i);
            if (curr.getStart() < prev.getEnd()) {
                throw new BadRequestException("Overlapping instructions: Previous End("
                                                  + prev.getEnd()
                                                  + "), Current Start("
                                                  + curr.getStart()
                                                  + ")");
            }
        }

        long currentTime = 0L;
        List<FfmpegEditInstructionDTO> invertedInstructions = new ArrayList<>();

        // invert
        for (var instruction : instructions) {
            if (instruction.getStart() == instruction.getEnd()) {
                throw new BadRequestException("Invalid instruction: Instruction with 0 second duration invalid: Start("
                                                  + instruction.getStart()
                                                  + "), End("
                                                  + instruction.getEnd()
                                                  + ")");
            }
            if (instruction.getEnd() < instruction.getStart()) {
                throw new BadRequestException("Invalid instruction: Instruction with end time before start time: Start("
                                                  + instruction.getStart()
                                                  + "), End("
                                                  + instruction.getEnd()
                                                  + ")");
            }
            if (instruction.getEnd() > recordingDuration) {
                throw new BadRequestException("Invalid instruction: Instruction end time exceeding duration: Start("
                                                  + instruction.getStart()
                                                  + "), End("
                                                  + instruction.getEnd()
                                                  + "), Recording Duration("
                                                  + recordingDuration
                                                  + ")");
            }
            if (currentTime < instruction.getStart()) {
                invertedInstructions.add(new FfmpegEditInstructionDTO(currentTime, instruction.getStart()));
            }
            currentTime = Math.max(currentTime, instruction.getEnd());
        }
        if (currentTime != recordingDuration) {
            invertedInstructions.add(new FfmpegEditInstructionDTO(currentTime,  recordingDuration));
        }

        return invertedInstructions;
    }

    protected List<EditCutInstructionDTO> combineCutsOnOriginalTimeline(
        final EditInstructions original,
        final List<EditCutInstructionDTO> newInstructions
    ) {
        final List<FfmpegEditInstructionDTO> keptSegments = original.getFfmpegInstructions();

        final List<FfmpegEditInstructionDTO> editedTimelineMapping = new ArrayList<>();
        long cursor = 0;
        for (FfmpegEditInstructionDTO segment : keptSegments) {
            long duration = segment.getEnd() - segment.getStart();
            editedTimelineMapping.add(new FfmpegEditInstructionDTO(cursor, cursor + duration));
            cursor += duration;
        }

        final List<EditCutInstructionDTO> mappedCuts = new ArrayList<>();
        for (EditCutInstructionDTO newCut : newInstructions) {
            for (int i = 0; i < keptSegments.size(); i++) {
                final FfmpegEditInstructionDTO originalSegment = keptSegments.get(i);
                final FfmpegEditInstructionDTO editedSegment = editedTimelineMapping.get(i);

                long start = editedSegment.getStart();
                long end = editedSegment.getEnd();

                if (newCut.getEnd() <= start || newCut.getStart() >= end) {
                    continue;
                }

                long overlapStart = Math.max(start, newCut.getStart());
                long overlapEnd = Math.min(end, newCut.getEnd());

                long offsetInSegment = overlapStart - start;
                long cutLength = overlapEnd - overlapStart;

                long originalMappedStart = originalSegment.getStart() + offsetInSegment;
                long originalMappedEnd = originalMappedStart + cutLength;

                mappedCuts.add(new EditCutInstructionDTO(originalMappedStart, originalMappedEnd, newCut.getReason()));
            }
        }

        mappedCuts.addAll(original.getRequestedInstructions());
        mappedCuts.sort(Comparator.comparing(EditCutInstructionDTO::getStart));

        return mergeOverlappingCuts(mappedCuts)
            .stream()
            .map(cut -> new EditCutInstructionDTO(cut.getStart(), cut.getEnd(), cut.getReason()))
            .collect(Collectors.toList());
    }

    protected List<EditCutInstructionDTO> mergeOverlappingCuts(final List<EditCutInstructionDTO> cuts) {
        final List<EditCutInstructionDTO> merged = new ArrayList<>();
        EditCutInstructionDTO current = cuts.getFirst();

        for (int i = 1; i < cuts.size(); i++) {
            final EditCutInstructionDTO next = cuts.get(i);
            if (next.getStart() <= current.getEnd()) {
                current.setEnd(Math.max(current.getEnd(), next.getEnd()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    protected String toJson(EditInstructions instructions) {
        try {
            return new ObjectMapper().writeValueAsString(instructions);
        } catch (JsonProcessingException e) {
            throw new UnknownServerException("Something went wrong: " + e.getMessage());
        }
    }
}
