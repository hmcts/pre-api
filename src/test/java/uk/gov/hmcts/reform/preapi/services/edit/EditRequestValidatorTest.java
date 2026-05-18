package uk.gov.hmcts.reform.preapi.services.edit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.services.edit.EditRequestTestUtils.createCut;
import static uk.gov.hmcts.reform.preapi.services.edit.EditRequestTestUtils.createSegment;

@SpringBootTest(classes = {EditRequestValidator.class})
public class EditRequestValidatorTest {

    @MockitoBean
    EditRequestValidator underTest;

    @Test
    @DisplayName("Edit requests must have a source recording")
    void ensureEditRequestHasSourceRecording() {
        EditRequest mockEditRequest = mock(EditRequest.class);
        when(mockEditRequest.getSourceRecording()).thenReturn(null);

        assertThrows(
            BadRequestException.class,
            () -> EditRequestValidator.ensureEditRequestHasSourceRecording(mockEditRequest)
        );
    }

    @Test
    @DisplayName("Disallow force re-encode and edit instructions on the same edit request")
    void validateEditMode() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setEditInstructions(EditRequestTestUtils.getSampleEditCutInstructions());
        dto.setForceReencode(true);

        assertThrows(BadRequestException.class, () -> EditRequestValidator.validateEditMode(dto));
    }

    @Test
    @DisplayName("Status must be pending before edit is processed")
    void ensureStatusIsPendingBeforeEditIsProcessed() {
        EditRequestDTO dto = new EditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        assertDoesNotThrow(() -> EditRequestValidator.ensureStatusIsPendingBeforeProcessingEdit(dto));

        dto.setStatus(EditRequestStatus.DRAFT);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> EditRequestValidator.ensureStatusIsPendingBeforeProcessingEdit(dto)
        );

        dto.setStatus(EditRequestStatus.SUBMITTED);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> EditRequestValidator.ensureStatusIsPendingBeforeProcessingEdit(dto)
        );

        dto.setStatus(EditRequestStatus.COMPLETE);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> EditRequestValidator.ensureStatusIsPendingBeforeProcessingEdit(dto)
        );

        dto.setStatus(EditRequestStatus.REJECTED);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> EditRequestValidator.ensureStatusIsPendingBeforeProcessingEdit(dto)
        );
    }

    @Test
    @DisplayName("Edit Request recording must have valid file name")
    void checkEditRequestHasValidFileName() {
        assertThrows(
            NotFoundException.class,
            () -> EditRequestValidator.checkEditRequestHasValidFileName(null)
        );

        EditRequest editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        // no recording set
        assertThrows(
            NotFoundException.class,
            () -> EditRequestValidator.checkEditRequestHasValidFileName(editRequest)
        );

        Recording recording = new Recording();
        recording.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        // no filename set
        assertThrows(
            NotFoundException.class,
            () -> EditRequestValidator.checkEditRequestHasValidFileName(editRequest)
        );

        String filename = "test_filename";
        recording.setFilename(filename);

        // Happy path
        String outputFilename = EditRequestValidator.checkEditRequestHasValidFileName(editRequest);
        Assertions.assertEquals(filename, outputFilename);
    }

    @Test
    @DisplayName("Must provide either cut instructions or force re-encode")
    void checkEditRequestHasValidInstructions() {
        EditInstructions mockInstructions = mock(EditInstructions.class);
        List<FfmpegEditInstructionDTO> ffmpegInstructions = List.of(
            createSegment(0, 10),
            createSegment(20, 30)
        );

        // Happy path 1: Not forced re-encode, instructions are not empty
        when(mockInstructions.isForceReencode()).thenReturn(false);
        when(mockInstructions.getFfmpegInstructions()).thenReturn(ffmpegInstructions);
        assertDoesNotThrow(() -> EditRequestValidator.checkForNonEmptyInstructionsOrForceReencode(mockInstructions));

        // Happy path 2: Forced re-encode, empty instructions
        when(mockInstructions.isForceReencode()).thenReturn(true);
        when(mockInstructions.getFfmpegInstructions()).thenReturn(null);
        assertDoesNotThrow(() -> EditRequestValidator.checkForNonEmptyInstructionsOrForceReencode(mockInstructions));

        // Bad request 1: Forced re-encode, instructions are not empty
        when(mockInstructions.isForceReencode()).thenReturn(true);
        when(mockInstructions.getFfmpegInstructions()).thenReturn(ffmpegInstructions);
        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.checkForNonEmptyInstructionsOrForceReencode(mockInstructions));

        // Bad request 2: Not forced re-encode, instructions are empty
        when(mockInstructions.isForceReencode()).thenReturn(false);
        when(mockInstructions.getFfmpegInstructions()).thenReturn(null);
        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.checkForNonEmptyInstructionsOrForceReencode(mockInstructions));
    }

    @Test
    @DisplayName("Edits should not cut an entire recording")
    void checkEditsDoNotCutAnEntireRecording() {
        List<EditCutInstructionDTO> inputs = List.of(
            createCut(0, 20, "new edit instructions"));
        long shortRecordingLength = 20;

        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.checkThatEditsDoNotCutAnEntireRecording(inputs, shortRecordingLength));

        long longRecordingLength = 30;
        assertDoesNotThrow(
                     () -> EditRequestValidator.checkThatEditsDoNotCutAnEntireRecording(inputs, longRecordingLength));

        // For some reason, this check has been implemented only for singleton cuts. I don't know if this is intended
        // or not, but I'm putting a test here to document that this is current behaviour.
        List<EditCutInstructionDTO> twoInputs = List.of(
            createCut(0, 20, "new edit instructions"),
            createCut(21, 30, "new edit instructions"));

        assertDoesNotThrow(
            () -> EditRequestValidator.checkThatEditsDoNotCutAnEntireRecording(twoInputs, longRecordingLength));
    }

    @Test
    @DisplayName("Edit request instructions should not overlap")
    void checkEditsDoNotOverlap() {
        List<EditCutInstructionDTO> nonOverlapping = List.of(
            createCut(0, 20, "new edit instructions"),
            createCut(23, 30, "new edit instructions"));

        assertDoesNotThrow(
                     () -> EditRequestValidator.checkThatEditsDoNotOverlap(nonOverlapping));

        List<EditCutInstructionDTO> overlapping = List.of(
            createCut(0, 21, "new edit instructions"),
            createCut(20, 30, "new edit instructions"));

        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.checkThatEditsDoNotOverlap(overlapping));
    }

    @Test
    @DisplayName("Validate edit instructions")
    void validateEditInstructions() {
        final long startTime = 20;
        final long recordingLength = 80;

        EditCutInstructionDTO happyPath = createCut(startTime, 40, "This is a valid instruction");
        assertDoesNotThrow(
                     () -> EditRequestValidator.validateEditInstruction(happyPath, recordingLength));

        EditCutInstructionDTO sameStartAndEnd = createCut(startTime, 20, "DISALLOWED: same start and end");
        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.validateEditInstruction(sameStartAndEnd, recordingLength));

        EditCutInstructionDTO endBeforeStart = createCut(startTime, 10, "DISALLOWED: end before start");
        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.validateEditInstruction(endBeforeStart, recordingLength));

        EditCutInstructionDTO endAfterDuration = createCut(startTime, 90, "DISALLOWED: end after duration");
        assertThrows(BadRequestException.class,
                     () -> EditRequestValidator.validateEditInstruction(endAfterDuration, recordingLength));
    }

}
