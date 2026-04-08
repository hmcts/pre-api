package uk.gov.hmcts.reform.preapi.services.edit;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
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
import static org.assertj.core.api.Assertions.in;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO.toDTO;
import static uk.gov.hmcts.reform.preapi.util.HelperFactory.createSimpleEditRequest;

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
    private EditRequestCrudService editRequestCrudService;

    @MockitoBean
    private Recording mockParentRecording;

    @MockitoBean
    private RecordingDTO mockRecordingDTO;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private CaptureSessionDTO mockCaptureSession;

    @MockitoBean
    private EditRequestDTO mockEditRequestDto;

    private EditRequest realEditRequest;
    private EditRequestDTO realEditRequestDto;

    @MockitoBean
    private User mockUser;

    @Autowired
    private EditRequestProcessingService underTest;

    private static final UUID mockEditRequestId = UUID.randomUUID();
    private static final UUID mockRecordingId = UUID.randomUUID();
    private static final UUID mockParentRecordingId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(mockParentRecording.getId()).thenReturn(mockParentRecordingId);

        when(recordingService.findById(mockRecordingId)).thenReturn(mockRecordingDTO);
        when(mockRecordingDTO.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(mockRecordingDTO.getFilename()).thenReturn("filename");
        when(mockRecordingDTO.getParentRecordingId()).thenReturn(mockParentRecordingId);
        when(mockRecordingDTO.getEditRequest()).thenReturn(mockEditRequestDto);
        when(mockRecordingDTO.getCaptureSession()).thenReturn(mockCaptureSession);
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(2);

        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getSourceRecordingId()).thenReturn(mockRecordingId);

        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 10, 20, "reason"));
        when(mockEditRequest.getEditCutInstructions()).thenReturn(instructions);
        when(editRequestRepository.findById(mockEditRequestId)).thenReturn(Optional.of(mockEditRequest));

        when(mockEditRequestDto.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequestDto.getSourceRecordingId()).thenReturn(mockRecordingId);
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(toDTO(instructions));
        when(mockRecordingDTO.getEditRequest()).thenReturn(mockEditRequestDto);

        realEditRequest = createSimpleEditRequest(
            mockEditRequestId, mockRecordingId, instructions,
            EditRequestStatus.DRAFT, mockUser
        );

        realEditRequestDto = new EditRequestDTO(realEditRequest);
    }

    @Test
    @DisplayName("Should mark edit request as processing")
    void updateEditRequestProcessing() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(mockEditRequest.getCreatedBy()).thenReturn(mockUser);
        underTest.markAsProcessing(mockEditRequestId);

        verify(editRequestCrudService, times(1)).updateEditRequestStatus(mockEditRequestId, EditRequestStatus.PROCESSING);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
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

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
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

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
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

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should prepare for and perform edit")
    void prepareForAndPerformEditSuccess() throws InterruptedException {
        Integer nextVersionNumber = 4;
        String generatedFilename = "generated filename";

        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(nextVersionNumber);
        when(assetGenerationService.generateAsset(any(UUID.class), any(UUID.class))).thenReturn(generatedFilename);

        try {
            underTest.prepareForAndPerformEdit(realEditRequestDto, mockUser);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        verify(recordingService, times(1)).findById(mockRecordingId);

        verify(recordingService, times(1)).getNextVersionNumber(mockParentRecordingId);

        ArgumentCaptor<UUID> newRecIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> originalRecIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(assetGenerationService, times(1))
            .generateAsset(newRecIdCaptor.capture(), originalRecIdCaptor.capture());
        assertThat(originalRecIdCaptor.getValue()).isEqualTo(mockRecordingId);
        UUID newRecId = newRecIdCaptor.getValue();

        verify(editRequestCrudService, times(1))
            .createOrUpsertDraftEditRequestInstructions(realEditRequestDto, mockUser);

        ArgumentCaptor<CreateRecordingDTO> newRecordingCaptor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService, times(1)).upsert(newRecordingCaptor.capture());

        CreateRecordingDTO upsertedDto = newRecordingCaptor.getValue();
        assertThat(upsertedDto.getId()).isEqualTo(newRecId);
        assertThat(upsertedDto.getVersion()).isEqualTo(nextVersionNumber);
        assertThat(upsertedDto.getParentRecordingId()).isEqualTo(mockParentRecordingId);
        assertThat(upsertedDto.getFilename()).isEqualTo(generatedFilename);
        verify(recordingService, times(1)).upsert(upsertedDto);
        verify(recordingService, times(1)).findById(newRecId);

        verify(editingService, times(1)).performEdit(newRecId, mockRecordingDTO, realEditRequestDto);

        // Check that output recording ID is set on edit request
        ArgumentCaptor<EditRequestDTO> updatedEditRequestDtoCaptor = ArgumentCaptor.forClass(EditRequestDTO.class);
        verify(editRequestCrudService, times(1))
            .createOrUpsertDraftEditRequestInstructions(updatedEditRequestDtoCaptor.capture(), any(User.class));
        assertThat(updatedEditRequestDtoCaptor.getValue().getOutputRecordingId()).isEqualTo(newRecId);

        verify(editNotificationService, times(1)).editRequestStatusWasUpdated(realEditRequestDto);

        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should prepare for and perform edit with legacy instructions")
    void prepareForAndPerformEditWithLegacyInstructions() throws InterruptedException {
        Integer nextVersionNumber = 4;
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(nextVersionNumber);
        when(mockRecordingDTO.getDuration()).thenReturn(Duration.ofMinutes(10));

        when(mockRecordingDTO.getEditRequest()).thenReturn(null);
        when(mockRecordingDTO.getEditInstructions()).thenReturn("""
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
            underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editingService, times(1))
            .performEdit(any(UUID.class), mockRecordingDTO, mockEditRequestDto);
        verify(recordingService, times(1)).getNextVersionNumber(mockParentRecordingId);
        verify(assetGenerationService, times(1)).generateAsset(any(UUID.class), mockRecordingId);

        ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService, times(1)).upsert(captor.capture());

        CreateRecordingDTO upsertedDto = captor.getValue();
        assertThat(upsertedDto.getVersion()).isEqualTo(nextVersionNumber);
        assertThat(upsertedDto.getParentRecordingId()).isEqualTo(mockParentRecordingId);
        assertThat(upsertedDto.getFilename()).isEqualTo("TODO");

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit for non-existent recording")
    void validateNonExistentRecording() {
        when(recordingService.findById(mockRecordingId)).thenReturn(null);

        assertThrows(
            NotFoundException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        );

        verify(recordingService, times(1)).findById(mockRecordingId);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit for non-existent edit instructions")
    void prepareForAndPerformEdit() {
        when(mockRecordingDTO.getEditRequest()).thenReturn(null);
        when(mockRecordingDTO.getEditInstructions()).thenReturn(null);
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(null);

        assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        );

        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit for non-existent edit instructions")
    void shouldNotPerformEditForNonExistentInstructions() {
        when(mockRecordingDTO.getEditRequest()).thenReturn(null);
        when(mockRecordingDTO.getEditInstructions()).thenReturn(null);
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(null);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.DRAFT);

        assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        );

        verify(recordingService, times(1)).findById(mockRecordingId);

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<EditRequestStatus> statusCaptor = ArgumentCaptor.forClass(EditRequestStatus.class);

        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(uuidCaptor.capture(), statusCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(mockEditRequestId);
        assertThat(statusCaptor.getValue()).isEqualTo(EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should not perform edit when instruction cuts entire recording")
    void validateInstructionsBadRequestCutToZeroDuration() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 0, 180, "reason"));
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(toDTO(instructions));

        when(mockRecordingDTO.getDuration()).thenReturn(Duration.ofSeconds(180));

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot cut an entire recording: "
                           + "Start(00:00:00), End(00:03:00), "
                           + "Recording Duration(00:03:00)");

        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);
        verify(recordingService, times(1)).findById(mockRecordingId);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw bad request when instruction has same value for start and end")
    void validateInstructionsBadRequestStartEndEqual() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 60, 60, "reason"));
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(toDTO(instructions));

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with 0 second duration invalid: "
                           + "Start(00:01:00), End(00:01:00)");

        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time is less than start time")
    void validateInstructionsBadRequestEndLTStart() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 60, 50, "reason"));
        List<EditCutInstructionsDTO> dtoInstructions = toDTO(instructions);

        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(dtoInstructions);

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with end time before start time: "
                           + "Start(00:01:00), End(00:00:50)");


        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }


    @Test
    @DisplayName("Should throw bad request when instruction end time exceeds duration")
    void validateInstructionsBadRequestEndTimeExceedsDuration() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(
            UUID.randomUUID(),
            60,
            200, // duration is 180
            "reason"
        ));
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(toDTO(instructions));
        when(mockRecordingDTO.getDuration()).thenReturn(Duration.ofSeconds(180));

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction end time exceeding duration: "
                           + "Start(00:01:00), End(00:03:20), "
                           + "Recording Duration(00:03:00)");

        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }


    @Test
    @DisplayName("Should throw bad request when instructions overlap")
    void validateInstructionsOverlap() {
        List<EditCutInstructions> instructions = new ArrayList<>();
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 10, 30, "first edit"));
        instructions.add(new EditCutInstructions(UUID.randomUUID(), 20, 40, "overlapping"));
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(toDTO(instructions));
        when(mockRecordingDTO.getDuration()).thenReturn(Duration.ofSeconds(180));

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message).isEqualTo("Overlapping instructions: "
                                          + "Previous End(00:00:30), Current Start(00:00:20)");

        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a duration")
    void validateZeroRecordingDuration() {
        when(mockRecordingDTO.getDuration()).thenReturn(null);

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Cannot perform edit request for recording (" + mockRecordingId + "): duration was zero");

        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

    @Test
    @DisplayName("Should throw bad request when trying to perform edit with empty instructions")
    void validateEmptyInstructions() {
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(List.of());
        when(mockEditRequestDto.getStatus()).thenReturn(EditRequestStatus.DRAFT);

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.prepareForAndPerformEdit(mockEditRequestDto, mockUser)
        ).getMessage();

        assertThat(message).isEqualTo("Cannot perform edit request: no instructions were provided");
        verify(recordingService, times(1)).findById(mockRecordingId);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(mockEditRequestId, EditRequestStatus.ERROR);

        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(editNotificationService);
        verifyNoMoreInteractions(assetGenerationService);
    }

}
