package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
public class EditRequestServiceTest {

    @MockBean
    private EditRequestRepository editRequestRepository;

    @MockBean
    private RecordingRepository recordingRepository;

    @Autowired
    private EditRequestService editRequestService;


    @Test
    @DisplayName("Should return all pending edit requests")
    void getPendingEditRequestsSuccess() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        when(editRequestRepository.findAllByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING))
            .thenReturn(List.of(editRequest));

        var res = editRequestService.getPendingEditRequests();

        assertThat(res).isNotNull();
        assertThat(res.size()).isEqualTo(1);
        assertThat(res.getFirst().getId()).isEqualTo(editRequest.getId());
        assertThat(res.getFirst().getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findAllByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should perform edit request and return COMPLETE status")
    void performEditSuccess() throws InterruptedException {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        when(editRequestRepository.findById(editRequest.getId())).thenReturn(Optional.of(editRequest));

        var res = editRequestService.performEdit(editRequest.getId());

        assertThat(res).isEqualTo(EditRequestStatus.COMPLETE);

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, times(2)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        var id = UUID.randomUUID();

        when(editRequestRepository.findById(id)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.performEdit(id)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findById(id);
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should not perform edit and return null when status of edit request is not PENDING")
    void performEditStatusNotPending() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PROCESSING);

        when(editRequestRepository.findById(editRequest.getId())).thenReturn(Optional.of(editRequest));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> editRequestService.performEdit(editRequest.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource EditRequest("
                           + editRequest.getId()
                           + ") is in a PROCESSING state. Expected state is PENDING.");

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw lock error when encounters locked edit request")
    void performEditLocked() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        doThrow(PessimisticLockingFailureException.class).when(editRequestRepository).findById(editRequest.getId());

        assertThrows(
            PessimisticLockingFailureException.class,
            () -> editRequestService.performEdit(editRequest.getId())
        );

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should create a new edit request")
    void createEditRequestSuccess() {
        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setDuration(Duration.ofMinutes(3));

        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(sourceRecording.getId())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        var response = editRequestService.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.CREATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(sourceRecording.getId());
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should update an edit request")
    void updateEditRequestSuccess() {
        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setDuration(Duration.ofMinutes(3));

        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(sourceRecording.getId())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(editRequest));

        var response = editRequestService.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        assertThat(editRequest.getId()).isEqualTo(dto.getId());
        assertThat(editRequest.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(sourceRecording.getId());
        assertThat(editRequest.getEditInstruction())
            .contains("\"ffmpegInstructions\":[{\"start\":0,\"end\":60},{\"start\":120,\"end\":180}]");

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(sourceRecording.getId());
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw not found when source recording does not exist")
    void createEditRequestSourceRecordingNotFound() {
        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(UUID.randomUUID())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        when(recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.upsert(dto)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Recording: " + dto.getSourceRecordingId());

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(dto.getSourceRecordingId());
        verify(editRequestRepository, never()).findById(any());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a duration")
    void createEditRequestDurationIsNullError() {
        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());

        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(sourceRecording.getId())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> editRequestService.upsert(dto)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Source Recording (" + dto.getSourceRecordingId() + ") does not have a valid duration");

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(sourceRecording.getId());
        verify(editRequestRepository, never()).findById(any());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw bad request when instruction cuts entire recording")
    void invertInstructionsBadRequestCutToZeroDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(0L)
                             .end(180L)
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot cut an entire recording: Start(0), End(180), "
                           + "Recording Duration(180)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction has same value for start and end")
    void invertInstructionsBadRequestStartEndEqual() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(60L)
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with 0 second duration invalid: Start(60), End(60)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time is less than start time")
    void invertInstructionsBadRequestEndLTStart() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(50L)
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with end time before start time: Start(60), End(50)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time exceeds duration")
    void invertInstructionsBadRequestEndTimeExceedsDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(200L) // duration is 180
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction end time exceeding duration: Start(60), End(200), "
                           + "Recording Duration(180)");
    }

    @Test
    @DisplayName("Should return inverted instructions (ordered correctly)")
    void invertInstructionsSuccess() {
        List<EditCutInstructionDTO> instructions1 = new ArrayList<>();
        instructions1.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());
        instructions1.add(EditCutInstructionDTO.builder()
                              .start(61L)
                              .end(121L)
                              .build());



        List<EditCutInstructionDTO> instructions2 = new ArrayList<>();
        instructions2.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(120L)
                              .build());
        instructions2.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(121L)
                              .build());

        List<EditCutInstructionDTO> instructions3 = new ArrayList<>();
        instructions3.add(EditCutInstructionDTO.builder()
                              .start(61L)
                              .end(70L)
                              .build());
        instructions3.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(121L)
                              .build());

        var expectedInvertedInstructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(0)
                .end(60)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(121)
                .end(180)
                .build()
        );

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions1, recording));
        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions2, recording));
        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions3, recording));
    }

    private void assertEditInstructionsEq(List<FfmpegEditInstructionDTO> expected,
                                          List<FfmpegEditInstructionDTO> actual) {
        assertThat(actual.size()).isEqualTo(expected.size());

        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i).getStart()).isEqualTo(expected.get(i).getStart());
            assertThat(actual.get(i).getEnd()).isEqualTo(expected.get(i).getEnd());
        }
    }
}