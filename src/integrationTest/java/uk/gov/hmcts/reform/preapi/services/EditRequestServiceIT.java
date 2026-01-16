package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class EditRequestServiceIT extends IntegrationTestBase {

    @Autowired
    private EditRequestService editRequestService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    private CaptureSession captureSession;

    private User user;

    @BeforeEach
    public void setup() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(captureSession);

        user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);
    }

    @Test
    @Transactional
    public void searchEditRequests() {
        var recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording);

        var editRequest = HelperFactory.createEditRequest(
            UUID.randomUUID(),
            recording,
            "{}",
            EditRequestStatus.PENDING,
            user,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(editRequest);

        SearchEditRequests paramsForExistingEditRequest = new SearchEditRequests();
        paramsForExistingEditRequest.setSourceRecordingId(recording.getId());
        var requests1 = editRequestService.findAll(paramsForExistingEditRequest, Pageable.unpaged()).toList();
        assertThat(requests1).hasSize(1);
        assertThat(requests1.getFirst().getId()).isEqualTo(editRequest.getId());

        SearchEditRequests paramsForRandomRecording = new SearchEditRequests();
        paramsForRandomRecording.setSourceRecordingId(UUID.randomUUID());
        var requests2 = editRequestService.findAll(paramsForRandomRecording, Pageable.unpaged()).toList();
        assertThat(requests2).isEmpty();

        var requests3 = editRequestService.findAll(new SearchEditRequests(), Pageable.unpaged()).toList();
        assertThat(requests3).hasSize(1);
        assertThat(requests3.getFirst().getId()).isEqualTo(editRequest.getId());
    }

    @Test
    @Transactional
    public void deleteEditRequest() {
        var recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);

        entityManager.persist(recording);

        UUID editRequestId = UUID.randomUUID();
        var editRequest = HelperFactory.createEditRequest(
            editRequestId,
            recording,
            "{\"ffmpegInstructions\":[{\"start\":0,\"end\":60},{\"start\":120,\"end\":180}]}",
            EditRequestStatus.PENDING,
            user,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(editRequest);

        SearchEditRequests paramsForExistingEditRequest = new SearchEditRequests();
        paramsForExistingEditRequest.setSourceRecordingId(recording.getId());
        var requests1 = editRequestService.findAll(paramsForExistingEditRequest, Pageable.unpaged()).toList();
        assertThat(requests1).hasSize(1);
        assertThat(requests1.getFirst().getId()).isEqualTo(editRequest.getId());

        CreateEditRequestDTO deleteRequest = new CreateEditRequestDTO();
        deleteRequest.setSourceRecordingId(recording.getId());
        deleteRequest.setId(editRequestId);
        deleteRequest.setEditInstructions(new ArrayList<>());

        editRequestService.delete(deleteRequest);

        var requests2 = editRequestService.findAll(paramsForExistingEditRequest, Pageable.unpaged()).toList();
        assertThat(requests2).isEmpty();
    }

    @Test
    @Transactional
    public void upsertWithEmptyInstructionsShouldDeleteEditRequest() {
        var recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording);

        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(recording.getDuration());
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString())).thenReturn("filename");

        UUID editRequestId = UUID.randomUUID();
        var editRequest = HelperFactory.createEditRequest(
            editRequestId,
            recording,
            "{\"ffmpegInstructions\":[{\"start\":0,\"end\":60},{\"start\":120,\"end\":180}]}",
            EditRequestStatus.PENDING,
            user,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(editRequest);

        SearchEditRequests paramsForExistingEditRequest = new SearchEditRequests();
        paramsForExistingEditRequest.setSourceRecordingId(recording.getId());
        var requests1 = editRequestService.findAll(paramsForExistingEditRequest, Pageable.unpaged()).toList();
        assertThat(requests1).hasSize(1);
        assertThat(requests1.getFirst().getId()).isEqualTo(editRequest.getId());

        CreateEditRequestDTO upsertRequest = new CreateEditRequestDTO();
        upsertRequest.setSourceRecordingId(recording.getId());
        upsertRequest.setId(editRequestId);
        upsertRequest.setEditInstructions(new ArrayList<>()); // Intentionally empty

        UpsertResult upsertResult = editRequestService.upsert(upsertRequest);
        assertThat(upsertResult).isEqualTo(UpsertResult.UPDATED);

        var requests2 = editRequestService.findAll(paramsForExistingEditRequest, Pageable.unpaged()).toList();
        assertThat(requests2).isEmpty();
    }
}
