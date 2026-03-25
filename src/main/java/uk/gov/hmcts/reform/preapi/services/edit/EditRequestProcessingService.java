package uk.gov.hmcts.reform.preapi.services.edit;

import com.azure.resourcemanager.mediaservices.models.JobState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.EditNotificationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO.formatTimeAsString;

@Service
@Slf4j
public class EditRequestProcessingService {

    private final EditRequestRepository editRequestRepository;
    private final IEditingService editingService;
    private final RecordingService recordingService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final MediaServiceBroker mediaServiceBroker;
    private final EditNotificationService editNotificationService;
    private final BookingService bookingService;

    @Autowired
    public EditRequestProcessingService(final EditRequestRepository editRequestRepository,
                                        final IEditingService editingService,
                                        final RecordingService recordingService,
                                        final AzureIngestStorageService azureIngestStorageService,
                                        final AzureFinalStorageService azureFinalStorageService,
                                        final MediaServiceBroker mediaServiceBroker,
                                        final EditNotificationService editNotificationService,
                                        final BookingService bookingService) {
        this.editRequestRepository = editRequestRepository;
        this.editingService = editingService;
        this.recordingService = recordingService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.mediaServiceBroker = mediaServiceBroker;
        this.editNotificationService = editNotificationService;
        this.bookingService = bookingService;
    }

    @Transactional(noRollbackFor = Exception.class)
    public EditRequest markAsProcessing(UUID editId) {
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
    public RecordingDTO prepareForAndPerformEdit(EditRequest request) throws InterruptedException {
        RecordingDTO sourceRecording = recordingService.findById(request.getSourceRecordingId());

        UUID newRecordingId = UUID.randomUUID();
        String filename;
        try {
            validateEditInstructions(sourceRecording);
            editingService.performEdit(newRecordingId, sourceRecording);
            filename = generateAsset(newRecordingId, request);
            Integer versionNumber = recordingService.getNextVersionNumber(sourceRecording.getParentRecordingId());
            CreateRecordingDTO createDto = createRecordingDto(newRecordingId, filename, versionNumber, sourceRecording);
            recordingService.upsert(createDto);
        } catch (Exception e) {
            updateEditRequestStatus(request.getId(), EditRequestStatus.ERROR);
            throw e;
        }

        updateEditRequestStatus(request.getId(), EditRequestStatus.COMPLETE);

        return recordingService.findById(newRecordingId);
    }

    private @NotNull CreateRecordingDTO createRecordingDto(UUID newRecordingId,
                                                          String filename,
                                                          Integer versionNumber,
                                                          RecordingDTO sourceRecording) {
        CreateRecordingDTO createDto = new CreateRecordingDTO(sourceRecording);

        UUID parentId = sourceRecording.getParentRecordingId() == null
            ? sourceRecording.getId()
            : sourceRecording.getParentRecordingId();
        createDto.setParentRecordingId(parentId);

        createDto.setId(newRecordingId);
        createDto.setVersion(versionNumber);
        createDto.setFilename(filename);
        return createDto;
    }

    private String generateAsset(UUID newRecordingId, EditRequest request) throws InterruptedException {
        String sourceContainer = newRecordingId + "-input";
        if (!azureIngestStorageService.doesContainerExist(sourceContainer)) {
            throw new NotFoundException("Source Container (" + sourceContainer + ") does not exist");
        }
        // throws 404 when doesn't exist
        azureIngestStorageService.getMp4FileName(sourceContainer);
        azureIngestStorageService.markContainerAsProcessing(sourceContainer);
        String assetName = newRecordingId.toString().replace("-", "");

        azureFinalStorageService.createContainerIfNotExists(newRecordingId.toString());

        GenerateAssetDTO generateAssetDto = GenerateAssetDTO.builder()
            .sourceContainer(sourceContainer)
            .destinationContainer(newRecordingId)
            .tempAsset(assetName)
            .finalAsset(assetName + "_output")
            .parentRecordingId(request.getSourceRecordingId())
            .description("Edit of " + request.getSourceRecordingId().toString().replace("-", ""))
            .build();

        GenerateAssetResponseDTO result = mediaServiceBroker.getEnabledMediaService()
            .importAsset(generateAssetDto, false);

        if (!result.getJobStatus().equals(JobState.FINISHED.toString())) {
            throw new UnknownServerException("Failed to generate asset for edit request: "
                                                 + request.getSourceRecordingId()
                                                 + ", new recording: "
                                                 + newRecordingId);
        }
        azureIngestStorageService.markContainerAsSafeToDelete(sourceContainer);
        return azureFinalStorageService.getMp4FileName(newRecordingId.toString());
    }

    @Transactional
    public void updateEditRequestStatus(UUID editRequestId, EditRequestStatus updatedStatus) {
        EditRequest editRequest = editRequestRepository.findById(editRequestId)
            .orElseThrow(() -> new NotFoundException("Edit Request: " + editRequestId));

        if (editRequest.getStatus() == EditRequestStatus.DRAFT && updatedStatus == EditRequestStatus.SUBMITTED) {
            if (editRequest.getEditCutInstructions().isEmpty()) {
                throw new BadRequestException("Cannot submit edit request with empty instructions");
            }
        }
        editRequest.setStatus(updatedStatus);

        if (updatedStatus == EditRequestStatus.PROCESSING) {
            editRequest.setStartedAt(Timestamp.from(Instant.now()));
        }

        if (updatedStatus == EditRequestStatus.COMPLETE || updatedStatus == EditRequestStatus.ERROR) {
            editRequest.setFinishedAt(Timestamp.from(Instant.now()));
        }
        editRequestRepository.save(editRequest);

        editNotificationService.editRequestStatusWasUpdated(editRequest);
    }


    private void validateEditInstructions(final RecordingDTO recordingDTO) {
        if (recordingDTO == null) {
            throw new BadRequestException("Cannot perform edit request: recording was null");
        }

        if (recordingDTO.getEditRequest() == null || recordingDTO.getEditRequest().getEditInstructions().isEmpty()) {
            throw new BadRequestException(format(
                "Edit request for empty for recording %s",
                recordingDTO.getId().toString()
            ));
        }
        long recordingDuration = recordingDTO.getDuration().toSeconds();

        List<EditCutInstructionsDTO> instructionsList = recordingDTO.getEditRequest().getEditInstructions();
        instructionsList.sort(Comparator.comparing(EditCutInstructionsDTO::getStart)
                                  .thenComparing(EditCutInstructionsDTO::getEnd));

        if (instructionsList.getFirst().getStart() == 0 && instructionsList.getFirst().getEnd() == recordingDuration) {
            throw new BadRequestException("Invalid Instruction: Cannot cut an entire recording: Start("
                                              + formatTimeAsString(instructionsList.getFirst().getStart())
                                              + "), End("
                                              + formatTimeAsString(instructionsList.getFirst().getEnd())
                                              + "), Recording Duration("
                                              + recordingDuration
                                              + " seconds)");
        }

        EditCutInstructionsDTO previous = null;
        for (EditCutInstructionsDTO current : instructionsList) {
            // To skip the first one
            if (previous == null) {
                previous = current;
                continue;
            }
            previous = current;

            if (current.getStart() < previous.getEnd()) {
                throw new BadRequestException(format(
                    "Overlapping instructions: Previous End(%s), Current Start(%s)",
                    formatTimeAsString(previous.getEnd()),
                    formatTimeAsString(previous.getStart())
                ));
            }

            if (current.getStart().equals(current.getEnd())) {
                throw new BadRequestException(format(
                    "Invalid instruction: Instruction with 0 second duration invalid: "
                        + "Start(%s), End(%s)",
                    formatTimeAsString(current.getStart()),
                    formatTimeAsString(current.getEnd())
                ));
            }
            if (current.getEnd() < current.getStart()) {
                throw new BadRequestException(format(
                    "Invalid instruction: Instruction with end time before start time: "
                        + "Start(%s), End(%s)",
                    formatTimeAsString(current.getStart()),
                    formatTimeAsString(current.getEnd())
                ));
            }
            if (current.getEnd() > recordingDuration) {
                throw new BadRequestException(format(
                    "Invalid instruction: Instruction end time exceeding duration: "
                        + "Start(%s), End(%s), Recording Duration(%s)",
                    formatTimeAsString(current.getStart()),
                    formatTimeAsString(current.getEnd()),
                    recordingDuration
                ));
            }
        }

    }

}
