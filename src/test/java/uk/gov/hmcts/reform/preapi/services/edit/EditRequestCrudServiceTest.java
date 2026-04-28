package uk.gov.hmcts.reform.preapi.services.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Pair;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.utils.JsonUtils.toJson;

@SpringBootTest(classes = EditRequestCrudService.class)
public class EditRequestCrudServiceTest {

    @MockitoBean
    private EditRequestRepository editRequestRepository;

    @MockitoBean
    private IEditingService editingService;

    @MockitoBean
    private CaptureSession captureSession;

    @MockitoBean
    private Booking booking;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private Recording mockParentRecording;

    @MockitoBean
    private User mockUser;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private CreateEditRequestDTO dto;

    @Autowired
    private EditRequestCrudService underTest;

    private static final UUID mockRecordingId = UUID.randomUUID();
    private static final UUID mockParentRecId = UUID.randomUUID();
    private static final UUID mockCaptureSessionId = UUID.randomUUID();
    private static final UUID mockEditRequestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(captureSession.getId()).thenReturn(mockCaptureSessionId);
        when(captureSession.getBooking()).thenReturn(booking);

        when(mockRecording.getId()).thenReturn(mockRecordingId);
        when(mockRecording.getCaptureSession()).thenReturn(captureSession);
        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(mockRecording.getFilename()).thenReturn("filename");

        when(mockParentRecording.getId()).thenReturn(mockParentRecId);
        when(mockParentRecording.getCaptureSession()).thenReturn(captureSession);

        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getSourceRecording()).thenReturn(mockRecording);
        when(mockEditRequest.getCreatedBy()).thenReturn(mockUser);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC");
        Case testCase = HelperFactory.createCase(court, "Test Case", false, null);

        ShareBooking shareBooking1 = HelperFactory.createShareBooking(
                new User(), mockUser, booking,
                new Timestamp(System.currentTimeMillis())
        );

        ShareBooking shareBooking2 = HelperFactory.createShareBooking(
                new User(), mockUser, booking,
                new Timestamp(System.currentTimeMillis())
        );

        when(booking.getShares()).thenReturn(Set.of(shareBooking1, shareBooking2));
        when(booking.getCaseId()).thenReturn(testCase);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        when(dto.getId()).thenReturn(UUID.randomUUID());
        when(dto.getSourceRecordingId()).thenReturn(mockRecordingId);
        when(dto.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(dto.getEditInstructions()).thenReturn(instructions);
    }

    @Test
    @DisplayName("Should return all pending edit requests")
    void getPendingEditRequestsSuccess() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(editRequestRepository.findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING))
            .thenReturn(Optional.of(mockEditRequest));

        Optional<EditRequest> res = underTest.getNextPendingEditRequest();

        assertThat(res).isPresent();
        assertThat(res.get().getId()).isEqualTo(mockEditRequestId);
        assertThat(res.get().getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should create a new edit request")
    void createEditRequestSuccess() {
        when(mockRecording.getParentRecording()).thenReturn(null);

        EditRequest editRequestToReturnFromEditingService = new EditRequest();
        editRequestToReturnFromEditingService.setId(dto.getId());
        editRequestToReturnFromEditingService.setStatus(dto.getStatus());
        editRequestToReturnFromEditingService.setSourceRecording(mockRecording);
        editRequestToReturnFromEditingService.setEditInstruction(toJson(dto.getEditInstructions()));

        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        when(editingService.prepareEditRequestToCreateOrUpdate(any(CreateEditRequestDTO.class), any(Recording.class),
                any(EditRequest.class))).thenReturn(editRequestToReturnFromEditingService);

        Pair<UpsertResult, EditRequest> response = underTest.upsert(dto, mockRecording, mockUser);
        assertThat(response.getFirst()).isEqualTo(UpsertResult.CREATED);
        assertThat(response.getSecond().getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(response.getSecond().getId()).isEqualTo(dto.getId());

        verify(editRequestRepository, times(1)).findByIdNotLocked(dto.getId());
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should return edit request when it exists")
    void findByIdSuccess() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(mockEditRequest.getEditInstruction()).thenReturn("{}");

        when(editRequestRepository.findByIdNotLocked(mockEditRequest.getId())).thenReturn(Optional.of(mockEditRequest));

        EditRequestDTO res = underTest.findById(mockEditRequest.getId());
        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(mockEditRequest.getId());
        assertThat(res.getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findByIdNotLocked(mockEditRequest.getId());
    }

    @Test
    @DisplayName("Should throw bad request when trying to create new edit request with empty instructions")
    void badRequestEmptyInstructions() {
        when(dto.getEditInstructions()).thenReturn(new ArrayList<>());

        String message = assertThrows(
                BadRequestException.class,
                () -> underTest.upsert(dto, mockRecording, mockUser)
        ).getMessage();

        assertThat(message)
                .isEqualTo("Invalid Instruction: Cannot create an edit request with empty instructions");
    }

    @Test
    @DisplayName("Should throw error when requested request does not exist")
    void findByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(editRequestRepository.findByIdNotLocked(id)).thenReturn(Optional.empty());

        String message = assertThrows(
                NotFoundException.class,
                () -> underTest.findById(id)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findByIdNotLocked(id);
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        UUID id = UUID.randomUUID();
        doThrow(NotFoundException.class).when(editRequestRepository).findById(any(UUID.class));

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.findById(id)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findByIdNotLocked(id);
        verifyNoMoreInteractions(editRequestRepository);
    }

    @Test
    @DisplayName("Should throw bad request when both cut instructions and force reencode are provided")
    void createEditRequestWithCutsAndForceReencode() {
        // dto edit instructions already created in test setup
        when(dto.isForceReencode()).thenReturn(true);

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(dto, mockRecording, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot request cuts and force reencode on the same edit request");

        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should delete edit request when upserting with empty instructions")
    void deleteEmptyInstructions() {
        when(dto.getEditInstructions()).thenReturn(new ArrayList<>());
        when(editRequestRepository.findByIdNotLocked(dto.getId()))
            .thenReturn(Optional.of(mockEditRequest));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(mockEditRequest));

        Pair<UpsertResult, EditRequest> result = underTest.upsert(dto, mockRecording, mockUser);
        assertThat(result.getFirst()).isEqualTo(UpsertResult.UPDATED);

        verify(editRequestRepository, times(1)).delete(result.getSecond());
    }

    @Test
    @DisplayName("Should throw exception when editInstructions is null for *new* edit request")
    void validateEditInstructionsIsEmptyForNewEditRequest() throws Exception {
        when(dto.getEditInstructions()).thenReturn(new ArrayList<>());
        when(dto.getStatus()).thenReturn(EditRequestStatus.DRAFT);
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(dto, mockRecording, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot create an edit request with empty instructions");
    }

    @Test
    @DisplayName("Should ignore attempt to delete non-existent edit request")
    void deleteNonExistentEditRequestSuccess() throws Exception {
        when(dto.getStatus()).thenReturn(EditRequestStatus.DRAFT);
        when(editRequestRepository.findById(dto.getSourceRecordingId()))
            .thenReturn(Optional.empty());

        underTest.delete(dto);

        verify(editRequestRepository, times(0)).delete(any());
    }

}
