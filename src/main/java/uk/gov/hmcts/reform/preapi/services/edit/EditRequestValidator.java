package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;

import java.util.List;

import static uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO.formatTime;

@Slf4j
public final class EditRequestValidator {
    protected static final Integer SINGLETON_LIST_SIZE = 1;

    private EditRequestValidator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    protected static void ensureEditRequestHasSourceRecording(EditRequest request) {
        if (request.getSourceRecording() == null) {
            throw new BadRequestException("Source Recording was null for edit request " + request.getId());
        }
    }

    protected static void validateEditMode(CreateEditRequestDTO dto) {
        log.debug("Validating edit request {}", dto);
        if (dto.isForceReencode() && dto.getEditInstructions() != null && !dto.getEditInstructions().isEmpty()) {
            throw new BadRequestException(
                "Invalid Instruction: Cannot request cuts and force reencode on the same edit request");
        }
    }

    protected static void ensureStatusIsPendingBeforeProcessingEdit(EditRequestDTO request) {
        if (request.getStatus() != EditRequestStatus.PENDING) {
            throw new ResourceInWrongStateException(
                EditRequest.class.getSimpleName(),
                request.getId().toString(),
                request.getStatus().toString(),
                EditRequestStatus.PENDING.toString()
            );
        }
    }

    protected static String checkEditRequestHasValidFileName(EditRequest request) {
        if (request == null) {
            throw new NotFoundException("Could not perform edit: request is null");
        }
        if (request.getSourceRecording() == null) {
            throw new NotFoundException("Could not perform edit: source recording is null for edit request "
                                            + request.getId());
        }
        if (request.getSourceRecording().getFilename() == null) {
            throw new NotFoundException("No file name provided for edit request " + request.getId());
        }
        return request.getSourceRecording().getFilename();
    }

    protected static void checkForNonEmptyInstructionsOrForceReencode(EditInstructions instructions) {
        if (instructions.isForceReencode()) {
            if (instructions.getFfmpegInstructions() != null && !instructions.getFfmpegInstructions().isEmpty()) {
                throw new UnknownServerException("Cannot force re-encode: edit instructions are not empty");
            }
        } else {
            if (instructions.getFfmpegInstructions() == null
                || instructions.getFfmpegInstructions().isEmpty()) {
                throw new UnknownServerException("No edit instructions received for edit request");
            }
        }
    }

    protected static void checkThatEditsDoNotCutAnEntireRecording(final List<EditCutInstructionDTO> instructions,
                                                                  final long recordingDuration) {
        if (instructions.size() == SINGLETON_LIST_SIZE) {
            EditCutInstructionDTO firstInstruction = instructions.getFirst();
            if (firstInstruction.getStart() == 0 && firstInstruction.getEnd() == recordingDuration) {
                throw new BadRequestException("Invalid Instruction: Cannot cut an entire recording: Start("
                                                  + formatTime(firstInstruction.getStart())
                                                  + "), End("
                                                  + formatTime(firstInstruction.getEnd())
                                                  + "), Recording Duration("
                                                  + formatTime(recordingDuration)
                                                  + ")");
            }
        }
    }

    protected static void checkThatEditsDoNotOverlap(final List<EditCutInstructionDTO> instructions) {
        for (int i = 1; i < instructions.size(); i++) {
            EditCutInstructionDTO prev = instructions.get(i - 1);
            EditCutInstructionDTO curr = instructions.get(i);
            if (curr.getStart() < prev.getEnd()) {
                throw new BadRequestException("Overlapping instructions: Previous End("
                                                  + formatTime(prev.getEnd())
                                                  + "), Current Start("
                                                  + formatTime(curr.getStart())
                                                  + ")");
            }
        }
    }

    protected static void validateEditInstruction(final EditCutInstructionDTO instruction,
                                                  final long recordingDuration) {
        if (instruction.getStart() == instruction.getEnd()) {
            throw new BadRequestException(
                "Invalid instruction: Instruction with 0 second duration invalid: Start("
                    + formatTime(instruction.getStart())
                    + "), End("
                    + formatTime(instruction.getEnd())
                    + ")");
        }
        if (instruction.getEnd() < instruction.getStart()) {
            throw new BadRequestException(
                "Invalid instruction: Instruction with end time before start time: Start("
                    + formatTime(instruction.getStart())
                    + "), End("
                    + formatTime(instruction.getEnd())
                    + ")");
        }
        if (instruction.getEnd() > recordingDuration) {
            throw new BadRequestException("Invalid instruction: Instruction end time exceeding duration: Start("
                                              + formatTime(instruction.getStart())
                                              + "), End("
                                              + formatTime(instruction.getEnd())
                                              + "), Recording Duration("
                                              + formatTime(recordingDuration)
                                              + ")");
        }
    }
}
