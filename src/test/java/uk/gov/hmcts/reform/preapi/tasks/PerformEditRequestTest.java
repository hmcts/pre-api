package uk.gov.hmcts.reform.preapi.tasks;

import org.hibernate.dialect.lock.PessimisticEntityLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PerformEditRequest.class)
public class PerformEditRequestTest {
    @MockitoBean
    private EditRequestService editRequestService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    private static final String CRON_USER_EMAIL = "test@test.com";

    private PerformEditRequest performEditRequest;

    @BeforeEach
    void setUp() {
        performEditRequest = new PerformEditRequest(
            editRequestService,
            userService,
            userAuthenticationService,
            CRON_USER_EMAIL
        );

        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        var access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(userAuth));
    }

    @Test
    @DisplayName("PerformEditRequest run")
    public void testRun() throws InterruptedException {
        var editRequest1 = createPendingEditRequest();
        var editRequest2 = createPendingEditRequest();
        var editRequest3 = createPendingEditRequest();
        var editRequest4 = createPendingEditRequest();
        var editRequest5 = createPendingEditRequest();
        var editRequest6 = createPendingEditRequest();

        when(editRequestService.getNextPendingEditRequest())
            .thenReturn(Optional.of(editRequest1))
            .thenReturn(Optional.of(editRequest2))
            .thenReturn(Optional.of(editRequest3))
            .thenReturn(Optional.of(editRequest4))
            .thenReturn(Optional.of(editRequest5))
            .thenReturn(Optional.of(editRequest6));
        when(editRequestService.markAsProcessing(editRequest1.getId())).thenReturn(editRequest1);
        when(editRequestService.markAsProcessing(editRequest2.getId())).thenReturn(editRequest2);
        when(editRequestService.markAsProcessing(editRequest5.getId())).thenReturn(editRequest5);
        // when request is successful
        when(editRequestService.performEdit(editRequest1)).thenReturn(new RecordingDTO());
        // when request errors in ffmpeg stage
        doThrow(UnknownServerException.class).when(editRequestService).performEdit(editRequest2);
        // when edit request is locked it should skip
        doThrow(PessimisticEntityLockException.class).when(editRequestService)
            .markAsProcessing(editRequest3.getId());
        // when edit request has already been updated to another state
        doThrow(ResourceInWrongStateException.class).when(editRequestService)
            .markAsProcessing(editRequest4.getId());
        // something else went wrong
        doThrow(NotFoundException.class).when(editRequestService)
            .markAsProcessing(editRequest5.getId());
        doThrow(InterruptedException.class).when(editRequestService)
            .markAsProcessing(editRequest6.getId());

        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();

        verify(editRequestService, times(6)).getNextPendingEditRequest();
        verify(editRequestService, times(1)).markAsProcessing(editRequest1.getId());
        verify(editRequestService, times(1)).performEdit(editRequest1);
        verify(editRequestService, times(1)).markAsProcessing(editRequest2.getId());
        verify(editRequestService, times(1)).performEdit(editRequest2);
        verify(editRequestService, times(1)).markAsProcessing(editRequest3.getId());
        verify(editRequestService, never()).performEdit(editRequest3);
        verify(editRequestService, times(1)).markAsProcessing(editRequest4.getId());
        verify(editRequestService, never()).performEdit(editRequest4);
        verify(editRequestService, times(1)).markAsProcessing(editRequest5.getId());
        verify(editRequestService, never()).performEdit(editRequest5);
        verify(editRequestService, times(1)).markAsProcessing(editRequest6.getId());
        verify(editRequestService, never()).performEdit(editRequest6);
    }

    @Test
    @DisplayName("PerformEditRequest run without any pending requests")
    void runNoPendingRequests() throws InterruptedException {

        performEditRequest.run();

        verify(editRequestService, times(1)).getNextPendingEditRequest();
        verify(editRequestService, never()).markAsProcessing(any());
        verify(editRequestService, never()).performEdit(any());
    }

    private EditRequest createPendingEditRequest() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);
        return editRequest;
    }
}
