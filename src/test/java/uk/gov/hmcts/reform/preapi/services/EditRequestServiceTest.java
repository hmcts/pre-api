package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestCrudService;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestFromCsv;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestProcessingService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
public class EditRequestServiceTest {
    @MockitoBean
    private EditRequestCrudService editRequestCrudService;

    @MockitoBean
    private EditRequestProcessingService editRequestProcessingService;

    @MockitoBean
    private EditRequestFromCsv csvService;

    @MockitoBean
    private UserAuthentication mockAuth;

    @MockitoBean
    private AppAccess mockAppAccess;

    @MockitoBean
    private SearchEditRequests searchEditRequests;

    @MockitoBean
    private Pageable pageable;

    private User courtClerkUser;

    private static final UUID editRequestId = UUID.randomUUID();
    private static final UUID recordingId = UUID.randomUUID();

    @Autowired
    private EditRequestService editRequestService;

    @BeforeEach
    void setup() {
        courtClerkUser = HelperFactory.createUser(
            "Court", "Clerk", "court.clerk@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        mockAppAccess.setUser(courtClerkUser);

        when(mockAuth.getAppAccess()).thenReturn(mockAppAccess);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

    @Test
    @DisplayName("Should pass on request for edit request by ID")
    void findEditRequestByIdSuccess() {
        editRequestService.findById(editRequestId);

        ArgumentCaptor<UUID> saveCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(editRequestCrudService, times(1)).findById(saveCaptor.capture());

        UUID editRequestId = saveCaptor.getValue();
        assertThat(editRequestId).isEqualTo(recordingId);
    }





    @Test
    @DisplayName("Should pass on request for all edit requests")
    void findAllEditRequestsSuccess() {
        editRequestService.findAll(searchEditRequests, pageable);

        verify(editRequestCrudService, times(1)).findAll(any(), any());
    }

    @Test
    @DisplayName("Search edit requests as admin user should not set additional filters")
    void findAllAsAdminUseSetsNullFilters() {
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(false);
        when(mockAuth.isPortalUser()).thenReturn(false);

        editRequestService.findAll(searchEditRequests, pageable);

        ArgumentCaptor<Pageable> pageableArgCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<SearchEditRequests> paramsArg = ArgumentCaptor.forClass(SearchEditRequests.class);

        verify(editRequestCrudService, times(1))
            .findAll(paramsArg.capture(), pageableArgCaptor.capture());

        assertThat(paramsArg.getAllValues()).isEmpty();
        assertThat(pageableArgCaptor.getAllValues()).isEqualTo(Pageable.unpaged());
    }

    @Test
    @DisplayName("Search edit requests as app user should set additional filters (court ID)")
    void findAllAsAppUserSetsCourtFilterOnly() {
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.isPortalUser()).thenReturn(false);
        UUID courtId = UUID.randomUUID();
        when(mockAuth.getCourtId()).thenReturn(courtId);

        editRequestService.findAll(searchEditRequests, pageable);

        ArgumentCaptor<Pageable> pageableArgCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<SearchEditRequests> paramsArg = ArgumentCaptor.forClass(SearchEditRequests.class);

        verify(editRequestCrudService, times(1))
            .findAll(paramsArg.capture(), pageableArgCaptor.capture());

        assertThat(paramsArg.getValue().getAuthorisedBookings()).isEmpty();
        assertThat(paramsArg.getValue().getAuthorisedCourt()).isEqualTo(courtId);
    }

    @Test
    @DisplayName("Search edit requests as portal user should set additional filters")
    void findAllAsPortalUserSetsAuthedBookingFilterOnly() {
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(false);
        when(mockAuth.isPortalUser()).thenReturn(true);
        when(mockAuth.getCourtId()).thenReturn(UUID.randomUUID());
        when(mockAuth.getSharedBookings()).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        editRequestService.findAll(searchEditRequests, pageable);

        ArgumentCaptor<Pageable> pageableArgCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<SearchEditRequests> paramsArg = ArgumentCaptor.forClass(SearchEditRequests.class);

        verify(editRequestCrudService, times(1))
            .findAll(paramsArg.capture(), pageableArgCaptor.capture());

        assertThat(paramsArg.getValue().getAuthorisedBookings()).containsAll(mockAuth.getSharedBookings());
        assertThat(paramsArg.getValue().getAuthorisedCourt()).isEqualTo(null);
    }

}
