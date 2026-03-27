package uk.gov.hmcts.reform.preapi.services.edit;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.services.EditNotificationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestProcessingService.class)
public class EditRequestProcessingServiceTest {

    @MockitoBean
    private EditRequestRepository editRequestRepository;

    @MockitoBean
    private IEditingService editingService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private EditNotificationService editNotificationService;

    @MockitoBean
    private AssetGenerationService assetGenerationService;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private Recording mockParentRecording;

    @MockitoBean
    private RecordingDTO mockRecordingDTO;

    @MockitoBean
    private EditRequest mockEditRequest;

    @Autowired
    private EditRequestProcessingService underTest;

    private static final UUID mockEditRequestId = UUID.randomUUID();
    private static final UUID mockRecordingId = UUID.randomUUID();
    private static final UUID mockParentRecordingId = UUID.randomUUID();
    @Autowired
    private EditRequestProcessingService editRequestProcessingService;

    @BeforeEach
    void setup() {
        when(mockRecording.getId()).thenReturn(mockRecordingId);
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(mockRecording.getFilename()).thenReturn("filename");
        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);
        when(mockRecording.getEditRequest()).thenReturn(mockEditRequest);
        when(mockParentRecording.getId()).thenReturn(mockParentRecordingId);

        when(recordingService.findById(mockRecordingId)).thenReturn(mockRecordingDTO);
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(2);

        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getSourceRecordingId()).thenReturn(mockRecordingId);
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 10, 20, "reason"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);
        when(editRequestRepository.findById(mockEditRequestId)).thenReturn(Optional.of(mockEditRequest));
    }

    @Test
    @DisplayName("Should update edit request status")
    void updateEditRequestProcessing() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        underTest.markAsProcessing(mockEditRequestId);

        verify(editRequestRepository, times(1)).save(mockEditRequest);
        verify(editNotificationService, times(1)).editRequestStatusWasUpdated(mockEditRequest);

        verifyNoMoreInteractions(editRequestRepository);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not update edit request status to SUBMITTED if edit instructions are empty")
    void shouldNotUpdateEditRequestProcessingIfEditInstructionsAreEmpty() {
        when(mockEditRequest.getEditCutInstructions()).thenReturn(List.of());
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.DRAFT);

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.updateEditRequestStatus(mockEditRequest.getId(), EditRequestStatus.SUBMITTED)
        ).getMessage();

        assertThat(message).isEqualTo(format(
                "Cannot submit edit request %s: empty instructions",
                mockEditRequestId));

        verifyNoMoreInteractions(editRequestRepository);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        when(editRequestRepository.findById(mockEditRequestId)).thenReturn(Optional.empty());

        var message = assertThrows(
                NotFoundException.class,
                () -> underTest.markAsProcessing(mockEditRequestId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Edit Request: " + mockEditRequestId);

        verify(editRequestRepository, times(1)).findById(mockEditRequestId);
        verifyNoMoreInteractions(editRequestRepository);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit and return null when status of edit request is not PENDING")
    void performEditStatusNotPending() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PROCESSING);

        String message = assertThrows(
                ResourceInWrongStateException.class,
                () -> underTest.markAsProcessing(mockEditRequestId)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Resource EditRequest("
                        + mockEditRequestId
                        + ") is in a PROCESSING state. Expected state is PENDING.");

        verify(editRequestRepository, times(1)).findById(mockEditRequestId);
        verifyNoMoreInteractions(editRequestRepository);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw lock error when encounters locked edit request")
    void performEditLocked() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);

        doThrow(PessimisticLockingFailureException.class)
                .when(editRequestRepository).findById(mockEditRequestId);

        assertThrows(
                PessimisticLockingFailureException.class,
                () -> underTest.markAsProcessing(mockEditRequestId)
        );

        verify(editRequestRepository, times(1)).findById(mockEditRequestId);
        verifyNoMoreInteractions(editRequestRepository);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should prepare for and perform edit")
    void prepareForAndPerformEditSuccess() throws InterruptedException {
        Integer nextVersionNumber = 4;
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(nextVersionNumber);

        try {
            underTest.prepareForAndPerformEdit(mockEditRequest);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        verify(recordingService.findById(mockRecordingId));
        verify(editingService, times(1)).performEdit(any(UUID.class), mockRecordingDTO);
        verify(recordingService.getNextVersionNumber(mockParentRecordingId));
        verify(assetGenerationService, times(1)).generateAsset(any(UUID.class), mockRecordingId);

        ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService.upsert(captor.capture()));

        CreateRecordingDTO upsertedDto = captor.getValue();
        assertThat(upsertedDto.getVersion()).isEqualTo(nextVersionNumber);
        assertThat(upsertedDto.getParentRecordingId()).isEqualTo(mockParentRecordingId);
        assertThat(upsertedDto.getFilename()).isEqualTo("TODO");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should prepare for and perform edit with legacy instructions")
    void prepareForAndPerformEditWithLegacyInstructions() throws InterruptedException {
        Integer nextVersionNumber = 4;
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(nextVersionNumber);
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(10));

        when(mockRecording.getEditRequest()).thenReturn(null);
        when(mockRecording.getEditInstruction()).thenReturn("""
                {
                          "requestedInstructions": [
                            {
                              "start_of_cut": "00:05:00",
                              "end_of_cut": "00:08:00",
                              "reason": "Removing 3 minutes",
                              "start": 300,
                              "end": 480
                            }
                          ],
                          "ffmpegInstructions": [
                            {
                              "start": 0,
                              "end": 300
                            },
                            {
                              "start": 480,
                              "end": 907
                            }
                          ]
                        }
                """);

        try {
            underTest.prepareForAndPerformEdit(mockEditRequest);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        verify(recordingService.findById(mockRecordingId));
        verify(editingService, times(1)).performEdit(any(UUID.class), mockRecordingDTO);
        verify(recordingService.getNextVersionNumber(mockParentRecordingId));
        verify(assetGenerationService, times(1)).generateAsset(any(UUID.class), mockRecordingId);

        ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService.upsert(captor.capture()));

        CreateRecordingDTO upsertedDto = captor.getValue();
        assertThat(upsertedDto.getVersion()).isEqualTo(nextVersionNumber);
        assertThat(upsertedDto.getParentRecordingId()).isEqualTo(mockParentRecordingId);
        assertThat(upsertedDto.getFilename()).isEqualTo("TODO");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit for non-existent recording")
    void validateNonExistentRecording() {
        when(recordingService.findById(mockRecordingId)).thenReturn(null);

        assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        );

        verify(recordingService.findById(mockRecordingId));
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit for non-existent edit instructions")
    void prepareForAndPerformEdit() {
        when(mockRecording.getEditRequest()).thenReturn(null);

        assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        );

        verify(recordingService.findById(mockRecordingId));
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit for non-existent edit instructions")
    void shouldNotPerformEditForNonExistentInstructions() {
        when(mockRecording.getEditRequest()).thenReturn(null);
        when(mockRecording.getEditInstruction()).thenReturn(null);

        assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        );

        verify(recordingService.findById(mockRecordingId));
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit when instruction cuts entire recording")
    void validateInstructionsBadRequestCutToZeroDuration() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 0, 180, "reason"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);

        when(mockRecording.getDuration()).thenReturn(Duration.ofSeconds(180));

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Invalid Instruction: Cannot cut an entire recording: "
                        + "Start(00:00:00), End(00:03:00), "
                        + "Recording Duration(00:03:00)");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw bad request when instruction has same value for start and end")
    void validateInstructionsBadRequestStartEndEqual() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 60, 60, "reason"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Invalid instruction: Instruction with 0 second duration invalid: "
                        + "Start(00:01:00), End(00:01:00)");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time is less than start time")
    void validateInstructionsBadRequestEndLTStart() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 60, 50, "reason"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Invalid instruction: Instruction with end time before start time: "
                        + "Start(00:01:00), End(00:00:50)");


        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }


    @Test
    @DisplayName("Should throw bad request when instruction end time exceeds duration")
    void validateInstructionsBadRequestEndTimeExceedsDuration() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(),
                60,
                200, // duration is 180
                "reason"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);
        when(mockRecording.getDuration()).thenReturn(Duration.ofSeconds(180));

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Invalid instruction: Instruction end time exceeding duration: "
                        + "Start(00:01:00), End(00:03:20), "
                        + "Recording Duration(00:03:00)");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }


    @Test
    @DisplayName("Should throw bad request when instructions overlap")
    void validateInstructionsOverlap() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 10, 30, "first edit"));
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 20, 40, "overlapping"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);
        when(mockRecording.getDuration()).thenReturn(Duration.ofSeconds(180));

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message).isEqualTo("Overlapping instructions: "
                + "Previous End(00:00:30), Current Start(00:00:20)");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a duration")
    void validateZeroRecordingDuration() {
        when(mockRecording.getDuration()).thenReturn(null);

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Source Recording (" + mockRecordingId + ") does not have a valid duration");

        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editRequestProcessingService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw bad request when trying to perform edit with empty instructions")
    void validateEmptyInstructions() {
        when(mockEditRequest.getEditCutInstructions()).thenReturn(List.of());
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.DRAFT);

        var message = assertThrows(
                BadRequestException.class,
                () -> underTest.prepareForAndPerformEdit(mockEditRequest)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Invalid Instruction: Cannot create an edit request with empty instructions");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }



}
