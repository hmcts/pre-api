package uk.gov.hmcts.reform.preapi.tasks;

import org.hibernate.dialect.lock.PessimisticEntityLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PerformEditRequest.class)
public class PerformEditRequestTest {
    @MockBean
    private EditRequestService editRequestService;

    @MockBean
    private UserService userService;

    @MockBean
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

        when(editRequestService.getNextPendingEditRequest())
            .thenReturn(Optional.of(editRequest1))
            .thenReturn(Optional.of(editRequest2))
            .thenReturn(Optional.of(editRequest3))
            .thenReturn(Optional.of(editRequest4))
            .thenReturn(Optional.of(editRequest5));

        // when request is successful
        when(editRequestService.performEdit(editRequest1.getId())).thenReturn(new RecordingDTO());
        // when request errors in ffmpeg stage
        doThrow(UnknownServerException.class).when(editRequestService).performEdit(editRequest2.getId());
        // when edit request is locked it should skip
        doThrow(PessimisticEntityLockException.class).when(editRequestService)
            .performEdit(editRequest3.getId());
        // when edit request has already been updated to another state
        doThrow(ResourceInDeletedStateException.class).when(editRequestService)
            .performEdit(editRequest4.getId());
        // something else went wrong
        doThrow(NotFoundException.class).when(editRequestService)
            .performEdit(editRequest5.getId());

        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();
        performEditRequest.run();

        verify(editRequestService, times(5)).getNextPendingEditRequest();
        verify(editRequestService, times(1))
            .updateEditRequestStatus(editRequest1.getId(), EditRequestStatus.PROCESSING);
        verify(editRequestService, times(1)).performEdit(editRequest1.getId());
        verify(editRequestService, times(1))
            .updateEditRequestStatus(editRequest2.getId(), EditRequestStatus.PROCESSING);
        verify(editRequestService, times(1)).performEdit(editRequest2.getId());
        verify(editRequestService, times(1))
            .updateEditRequestStatus(editRequest3.getId(), EditRequestStatus.PROCESSING);
        verify(editRequestService, times(1)).performEdit(editRequest3.getId());
        verify(editRequestService, times(1))
            .updateEditRequestStatus(editRequest4.getId(), EditRequestStatus.PROCESSING);
        verify(editRequestService, times(1)).performEdit(editRequest4.getId());
        verify(editRequestService, times(1))
            .updateEditRequestStatus(editRequest5.getId(), EditRequestStatus.PROCESSING);
        verify(editRequestService, times(1)).performEdit(editRequest5.getId());
    }

    private EditRequest createPendingEditRequest() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);
        return editRequest;
    }
}
