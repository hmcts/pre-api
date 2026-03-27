package uk.gov.hmcts.reform.preapi.services.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditCutInstructionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
public class EditRequestCrudServiceTest {
    @MockitoBean
    private EditRequestRepository editRequestRepository;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private EditCutInstructionsRepository editCutInstructionsRepository;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private EditRequestDTO mockEditRequestDTO;

    @MockitoBean
    private User mockUser;

    @Autowired
    private EditRequestCrudService underTest;

    private static final UUID mockEditRequestId = UUID.randomUUID();
    private static final UUID mockRecordingId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(mockRecording.getId()).thenReturn(mockRecordingId);
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(mockRecording.getFilename()).thenReturn("filename");
        when(mockRecording.getVersion()).thenReturn(1);
        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId)).thenReturn(Optional.of(mockRecording));

        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequestDTO.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getSourceRecordingId()).thenReturn(mockRecordingId);
        when(editRequestRepository.findById(mockEditRequestId)).thenReturn(Optional.of(mockEditRequest));
        when(editRequestRepository.findFirstBySourceRecordingIdIs(mockRecordingId))
            .thenReturn(Optional.of(mockEditRequest));

        EditCutInstructions editInstructions = new EditCutInstructions(
            UUID.randomUUID(),
            10, 20, "reason"
        );

        when(mockEditRequest.getEditCutInstructions()).thenReturn(List.of(editInstructions));
    }

    @Test
    @DisplayName("Should return edit request when it exists")
    void findByIdSuccess() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);

        EditRequestDTO res = underTest.findById(mockEditRequestId);
        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(mockEditRequestId);
        assertThat(res.getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findByIdNotLocked(mockEditRequestId);
    }

    @Test
    @DisplayName("Should throw error when requested request does not exist")
    void findByIdNotFound() {
        var id = UUID.randomUUID();
        when(editRequestRepository.findByIdNotLocked(id)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.findById(id)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findByIdNotLocked(id);
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

        verify(editRequestRepository, times(1))
            .findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should delete edit request")
    void deleteEditRequestSuccess() {
        when(editRequestRepository.findById(mockEditRequestId)).thenReturn(Optional.of(mockEditRequest));

        underTest.delete(mockEditRequestDTO);

        verify(editRequestRepository, times(0)).delete(mockEditRequest);
    }

    @Test
    @DisplayName("Should ignore attempt to delete non-existent edit request")
    void deleteNonExistentEditRequestSuccess() {
        when(editRequestRepository.findById(mockEditRequestId)).thenReturn(Optional.empty());

        underTest.delete(mockEditRequestDTO);

        verify(editRequestRepository, times(0)).delete(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should verify that recording exists before creating edit request")
    void verifyThatRecordingExistsBeforeCreatingEditRequestSuccess() {
        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId)).thenReturn(Optional.empty());

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.createOrUpsertDraftEditRequestInstructions(mockEditRequestDTO, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Source Recording: " + mockRecordingId);
    }

    @Test
    @DisplayName("Should verify that recording has non-zero duration before creating edit request")
    void verifyThatRecordingHasNonZeroDurationBeforeCreatingEditRequestSuccess() {
        when(mockRecording.getDuration()).thenReturn(Duration.ZERO);

        String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.createOrUpsertDraftEditRequestInstructions(mockEditRequestDTO, mockUser)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Source Recording (" + mockRecordingId + ") does not have a valid duration");
    }

    @Test
    @DisplayName("Can create a brand new edit request")
    void createNewEditRequestSuccess() {
        when(editRequestRepository.findFirstBySourceRecordingIdIs(mockRecordingId)).thenReturn(Optional.empty());

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(mockUser);
        assertThat(captor.getValue().getStatus()).isEqualTo(EditRequestStatus.DRAFT);
        assertThat(captor.getValue().getSourceRecordingId()).isEqualTo(mockRecordingId);
        assertThat(captor.getValue().getEditCutInstructions()).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Inserts a new edit request if previous edit requests are not in draft status")
    void insertNewEditRequestIfNoExistingDraftSuccess() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(mockUser);
        assertThat(captor.getValue().getStatus()).isEqualTo(EditRequestStatus.DRAFT);
        assertThat(captor.getValue().getSourceRecordingId()).isEqualTo(mockRecordingId);
        assertThat(captor.getValue().getEditCutInstructions()).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Upserts existing edit request if a draft exists")
    void upsertExistingDraftSuccess() {
        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(mockUser);
        assertThat(captor.getValue().getStatus()).isEqualTo(EditRequestStatus.DRAFT);
        assertThat(captor.getValue().getSourceRecordingId()).isEqualTo(mockRecordingId);
        assertThat(captor.getValue().getEditCutInstructions()).isEqualTo(mockEditRequest.getEditCutInstructions());
    }

    @Test
    @DisplayName("Will not create an edit request from a non-original recording")
    void willNotCreateEditRequestFromNonOriginalRecordingSuccess() {
        when(editRequestRepository.findFirstBySourceRecordingIdIs(mockRecordingId)).thenReturn(Optional.empty());
        when(mockRecording.getVersion()).thenReturn(3);

        Recording mockParentRecording = mock(Recording.class);
        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.createOrUpsertDraftEditRequestInstructions(mockEditRequestDTO, mockUser)
        ).getMessage();

        assertThat(message).isEqualTo(
            "Can only perform edits on original recording (Version 1). "
                + "Recording %s is version %d. Perhaps you need parent recording %s?",
            mockRecordingId, 3, any(UUID.class)
        );
    }

    @Test
    @DisplayName("Should be able to delete edit instructions for *existing* draft edit request")
    void validateEditInstructionsIsEmpty() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(mockEditRequest.getEditCutInstructions()).thenReturn(List.of());

        EditRequestDTO result = underTest.createOrUpsertDraftEditRequestInstructions(
            mockEditRequestDTO,
            mockUser
        );

        assertThat(result).isEqualTo(mockEditRequestDTO);

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEditCutInstructions()).isEmpty();
    }




}
