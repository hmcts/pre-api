package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestCrudService;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestFromCsv;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestProcessingService;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class EditRequestService {

    private final EditRequestCrudService editRequestCrudService;
    private final EditRequestProcessingService editRequestProcessingService;
    private final EditRequestFromCsv editRequestFromCsv;

    @Autowired
    public EditRequestService(final EditRequestCrudService editRequestCrudService,
                              final EditRequestProcessingService editRequestProcessingService,
                              final EditRequestFromCsv editRequestFromCsv) {
        this.editRequestCrudService = editRequestCrudService;
        this.editRequestProcessingService = editRequestProcessingService;
        this.editRequestFromCsv = editRequestFromCsv;
    }

    @PreAuthorize("@authorisationService.hasEditRequestAccess(authentication, #id)")
    public EditRequestDTO findById(UUID id) {
        return editRequestCrudService.findById(id);
    }

    public Page<EditRequestDTO> findAll(@NotNull SearchEditRequests params, Pageable pageable) {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        params.setAuthorisedBookings(auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings());
        params.setAuthorisedCourt(auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId());

        return editRequestCrudService.findAll(params, pageable);
    }

    public Optional<EditRequest> getNextPendingEditRequest() {
        return editRequestCrudService.getNextPendingEditRequest();
    }

    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #dto)")
    public EditRequestDTO upsert(EditRequestDTO dto) {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        User user = auth.isAppUser() ? auth.getAppAccess().getUser() : auth.getPortalAccess().getUser();
        return editRequestCrudService.createOrUpsertDraftEditRequestInstructions(dto, user);
    }

    // temporary code for create edit request with csv endpoint
    @Deprecated
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #sourceRecordingId)")
    public EditRequestDTO upsert(UUID sourceRecordingId, MultipartFile file) {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        User user = auth.isAppUser() ? auth.getAppAccess().getUser() : auth.getPortalAccess().getUser();
        return editRequestFromCsv.upsert(sourceRecordingId, file, user);

    }

    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #dto)")
    public void delete(EditRequestDTO dto) {
        editRequestCrudService.delete(dto);
    }

    public EditRequest markAsProcessing(UUID editId) {
        return editRequestProcessingService.markAsProcessing(editId);
    }

    public RecordingDTO performEdit(EditRequestDTO request) throws InterruptedException {
        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        User user = auth.isAppUser() ? auth.getAppAccess().getUser() : auth.getPortalAccess().getUser();
        return editRequestProcessingService.prepareForAndPerformEdit(request, user);
    }

    public void updateEditRequestStatus(UUID id, EditRequestStatus updatedStatus) {
        editRequestCrudService.updateEditRequestStatus(id, updatedStatus);
    }

}
