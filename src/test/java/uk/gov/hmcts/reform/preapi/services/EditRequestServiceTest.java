package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
public class EditRequestServiceTest {

    @MockitoBean
    private EditRequestRepository editRequestRepository;

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
}
