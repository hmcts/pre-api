package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.services.EditNotificationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.preapi.utils.StringTools.formatDurationAsString;
import static uk.gov.hmcts.reform.preapi.utils.StringTools.formatTimeAsString;
import static uk.gov.hmcts.reform.preapi.utils.StringTools.isBlankString;

@Service
@Slf4j
public class EditRequestProcessingService {

    private final EditRequestRepository editRequestRepository;
    private final EditRequestCrudService editRequestCrudService;
    private final IEditingService editingService;
    private final RecordingService recordingService;
    private final EditNotificationService editNotificationService;
    private final AssetGenerationService assetGenerationService;

    @Autowired
    public EditRequestProcessingService(final EditRequestRepository editRequestRepository,
                                        final EditRequestCrudService editRequestCrudService,
                                        final IEditingService editingService,
                                        final RecordingService recordingService,
                                        final EditNotificationService editNotificationService,
                                        final AssetGenerationService assetGenerationService) {
        this.editRequestRepository = editRequestRepository;
        this.editRequestCrudService = editRequestCrudService;
        this.editingService = editingService;
        this.recordingService = recordingService;
        this.editNotificationService = editNotificationService;
        this.assetGenerationService = assetGenerationService;
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
        editRequestCrudService.updateEditRequestStatus(request.getId(), EditRequestStatus.PROCESSING);
        return request;
    }

    @Transactional(noRollbackFor = {Exception.class, RuntimeException.class}, propagation = Propagation.REQUIRES_NEW)
    public RecordingDTO prepareForAndPerformEdit(EditRequestDTO request, User user) throws InterruptedException {
        RecordingDTO sourceRecording = recordingService.findById(request.getSourceRecordingId());

        if (sourceRecording == null) {
            throw new NotFoundException(
                format("Recording with id %s not found", request.getSourceRecordingId()));
        }

        UUID newRecordingId = UUID.randomUUID();
        String filename;
        List<EditCutInstructionsDTO> instructionsList = getEditCutInstructionsLegacyProof(
            request,
            sourceRecording.getEditInstructions()
        );
        try {
            validateEditInstructions(request.getSourceRecordingId(), sourceRecording.getDuration(), instructionsList);
            editingService.performEdit(newRecordingId, sourceRecording, request);
            filename = assetGenerationService.generateAsset(newRecordingId, request.getSourceRecordingId());
            Integer versionNumber = recordingService.getNextVersionNumber(sourceRecording.getParentRecordingId());
            CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(
                newRecordingId, filename, versionNumber, sourceRecording);
            request.setOutputRecordingId(createRecordingDTO.getId());
            request.setStatus(EditRequestStatus.COMPLETE);

            recordingService.upsert(createRecordingDTO);
            editRequestCrudService.createOrUpsertDraftEditRequestInstructions(request, user);
            editNotificationService.editRequestStatusWasUpdated(request);
        } catch (Exception e) {
            editRequestCrudService.updateEditRequestStatus(request.getId(), EditRequestStatus.ERROR);
            throw e;
        }
        return recordingService.findById(newRecordingId);
    }

    private void validateEditInstructions(final UUID sourceRecordingId,
                                          final Duration recordingDuration,
                                          final List<EditCutInstructionsDTO> instructionsList) {
        if (instructionsList.isEmpty()) {
            throw new BadRequestException("Cannot perform edit request: no instructions were provided");
        }

        instructionsList.sort(Comparator.comparing(EditCutInstructionsDTO::getStart)
                                  .thenComparing(EditCutInstructionsDTO::getEnd));

        if (recordingDuration == null || recordingDuration.isZero()) {
            throw new BadRequestException(
                format("Cannot perform edit request for recording (%s): duration was zero", sourceRecordingId));
        }

        if (instructionsList.getFirst().getStart() == 0
            && instructionsList.getFirst().getEnd() == recordingDuration.toSeconds()) {
            throw new BadRequestException(format(
                "Invalid Instruction: Cannot cut an entire recording: Start(%s), End(%s), Recording Duration(%s)",
                formatTimeAsString(instructionsList.getFirst().getStart()),
                formatTimeAsString(instructionsList.getFirst().getEnd()),
                formatDurationAsString(recordingDuration)
            ));
        }

        EditCutInstructionsDTO previous = null;
        for (EditCutInstructionsDTO current : instructionsList) {
            if (current.getStart().equals(current.getEnd())) {
                throw new BadRequestException(format(
                    "Invalid instruction: Instruction with 0 second duration invalid: Start(%s), End(%s)",
                    formatTimeAsString(current.getStart()),
                    formatTimeAsString(current.getEnd())
                ));
            }
            if (current.getEnd() < current.getStart()) {
                throw new BadRequestException(format(
                    "Invalid instruction: Instruction with end time before start time: Start(%s), End(%s)",
                    formatTimeAsString(current.getStart()),
                    formatTimeAsString(current.getEnd())
                ));
            }
            if (current.getEnd() > recordingDuration.toSeconds()) {
                throw new BadRequestException(format(
                    "Invalid instruction: Instruction end time exceeding duration: "
                        + "Start(%s), End(%s), Recording Duration(%s)",
                    formatTimeAsString(current.getStart()),
                    formatTimeAsString(current.getEnd()),
                    formatDurationAsString(recordingDuration)
                ));
            }

            // To skip the first one
            if (previous == null) {
                previous = current;
            } else {
                if (current.getStart() < previous.getEnd()) {
                    throw new BadRequestException(format(
                        "Overlapping instructions: Previous End(%s), Current Start(%s)",
                        formatTimeAsString(previous.getEnd()),
                        formatTimeAsString(current.getStart())
                    ));
                }
                previous = current;
            }

        }

    }

    private List<EditCutInstructionsDTO> getEditCutInstructionsLegacyProof(final EditRequestDTO editRequest,
                                                                           final String legacyEditCutInstructions) {
        // Default to new-style instructions
        if (editRequest != null && editRequest.getEditCutInstructions() != null
            && !editRequest.getEditCutInstructions().isEmpty()) {
            return editRequest.getEditCutInstructions();
        }

        if (!isBlankString(legacyEditCutInstructions)) {
            List<EditCutInstructions> editCutInstructionsList =
                EditRequest.convertEditCutInstructionsFromJson(legacyEditCutInstructions);

            return EditRequestDTO.toDTO(editCutInstructionsList);
        }

        return List.of();
    }

}
