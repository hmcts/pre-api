package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// I split this out to capture the first half of the editing process, which doesn't yet have any tests
class EditControllerFullyAutomatedFT extends FunctionalTestBase {
    private static final String EDIT_ENDPOINT = "/edits";

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private RecordingRepository recordingRepository;

    private UUID recordingId;
    private RecordingDTO recordingDTO;


    @MockitoBean
    private Recording recording;

    @MockitoBean
    private CaptureSession captureSession;

    @MockitoBean
    private Booking booking;

    @MockitoBean
    private Case legalCase;

    @MockitoBean
    private Court mockCourt;


    @BeforeEach
    void setUp() {
        CreateRecordingResponse recordingDetails = createRecording();
        recordingId = recordingDetails.recordingId();
        when(recording.getId()).thenReturn(recordingId);
        when(recording.getCaptureSession()).thenReturn(captureSession);

        when(captureSession.getId()).thenReturn(recordingDetails.captureSessionId());
        when(captureSession.getBooking()).thenReturn(booking);

        when(booking.getId()).thenReturn(recordingDetails.bookingId());
        when(booking.getCaseId()).thenReturn(legalCase);

        when(legalCase.getCourt()).thenReturn(mockCourt);
        when(mockCourt.getGroupEmail()).thenReturn("mock@email.com");

        when(recordingRepository.findAll()).thenReturn(List.of(recording));
        when(recordingRepository.findById(recordingId)).thenReturn(Optional.of(recording));
        when(recordingRepository.findByIdAndDeletedAtIsNull(recordingId)).thenReturn(Optional.of(recording));
        when(recordingRepository.findByIdAndDeletedAtIsNull(recordingId, true))
            .thenReturn(Optional.of(recording));
        when(recordingRepository.findByIdAndDeletedAtIsNull(recordingId, false))
            .thenReturn(Optional.of(recording));

        recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true)
            .as(RecordingDTO.class);

        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());


    }

    @Test
    @DisplayName("Should create a DRAFT edit request, update it and submit it, receiving a notification")
    void editRequestSuccess() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = new CreateEditRequestDTO();
        createEditRequestDTO.setId(UUID.randomUUID());
        createEditRequestDTO.setStatus(EditRequestStatus.DRAFT);
        createEditRequestDTO.setSourceRecordingId(recordingId);

        // Create as DRAFT
        Response firstResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + createEditRequestDTO.getId(),
            OBJECT_MAPPER.writeValueAsString(createEditRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        EditRequestDTO editRequestDTO = OBJECT_MAPPER.readValue(firstResponse.body().asString(), EditRequestDTO.class);

        Assertions.assertThat(editRequestDTO.getStatus()).isEqualTo(EditRequestStatus.DRAFT);
        Assertions.assertThat(editRequestDTO.getEditInstruction()).isNull();
        Assertions.assertThat(editRequestDTO.getSourceRecording()).isEqualTo(recordingDTO);

        // Update as DRAFT
        List<EditCutInstructionDTO> editInstructions = List.of(EditCutInstructionDTO.builder()
                                                                   .startOfCut("00:00:00")
                                                                   .endOfCut("00:00:01")
                                                                   .build());
        createEditRequestDTO.setEditInstructions(editInstructions);

        Response secondResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + recordingId,
            OBJECT_MAPPER.writeValueAsString(createEditRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        EditRequestDTO updatedDraft = OBJECT_MAPPER.readValue(firstResponse.body().asString(), EditRequestDTO.class);

        Assertions.assertThat(updatedDraft.getStatus()).isEqualTo(EditRequestStatus.DRAFT);
        Assertions.assertThat(updatedDraft.getEditInstruction().getRequestedInstructions())
            .isEqualTo(editInstructions);
        Assertions.assertThat(updatedDraft.getSourceRecording()).isEqualTo(recordingDTO);

        // Submit
        createEditRequestDTO.setStatus(EditRequestStatus.SUBMITTED);
        Response thirdResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + recordingId,
            OBJECT_MAPPER.writeValueAsString(createEditRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        EditRequestDTO submitted = OBJECT_MAPPER.readValue(firstResponse.body().asString(), EditRequestDTO.class);

        Assertions.assertThat(submitted.getStatus()).isEqualTo(EditRequestStatus.SUBMITTED);
        Assertions.assertThat(submitted.getEditInstruction().getRequestedInstructions())
            .isEqualTo(editInstructions);
        Assertions.assertThat(submitted.getSourceRecording()).isEqualTo(recordingDTO);
    }

    @Test
    @DisplayName("An edit request should be read-only once submitted")
    void editRequestShouldBeReadOnlyOnceSubmitted() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = new CreateEditRequestDTO();
        UUID editRequestId = UUID.randomUUID();
        createEditRequestDTO.setId(editRequestId);
        createEditRequestDTO.setSourceRecordingId(recordingId);
        List<EditCutInstructionDTO> editInstructions = List.of(EditCutInstructionDTO.builder()
                                                                   .startOfCut("00:00:00")
                                                                   .endOfCut("00:00:01")
                                                                   .build());
        createEditRequestDTO.setEditInstructions(editInstructions);
        createEditRequestDTO.setJointlyAgreed(true);


        when(recordingRepository.findByIdAndDeletedAtIsNull(editRequestId)).thenReturn(Optional.of(recording));

        // Submit
        createEditRequestDTO.setStatus(EditRequestStatus.SUBMITTED);
        String requestBody = OBJECT_MAPPER.writeValueAsString(createEditRequestDTO);
        Response firstResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + editRequestId,
            requestBody,
            TestingSupportRoles.SUPER_USER
        );

        EditRequestDTO submitted = OBJECT_MAPPER.readValue(firstResponse.body().asString(), EditRequestDTO.class);

        Assertions.assertThat(submitted.getStatus()).isEqualTo(EditRequestStatus.SUBMITTED);
        Assertions.assertThat(submitted.getEditInstruction().getRequestedInstructions())
            .isEqualTo(editInstructions);
        Assertions.assertThat(submitted.getSourceRecording()).isEqualTo(recordingDTO);

        // Should be read-only once submitted
        List<EditCutInstructionDTO> updatedInstructions = List.of(EditCutInstructionDTO.builder()
                                                                      .startOfCut("00:00:00")
                                                                      .endOfCut("00:00:01")
                                                                      .build());
        createEditRequestDTO.setEditInstructions(updatedInstructions);
        String resubmittedRequest = OBJECT_MAPPER.writeValueAsString(createEditRequestDTO);
        String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> doPutRequest(
                EDIT_ENDPOINT + "/" + recordingId,
                resubmittedRequest,
                TestingSupportRoles.SUPER_USER
            )
        ).getMessage();

        Assertions.assertThat(message)
            .isEqualTo(
                "Cannot resubmit edit request {}: submit a new edit request",
                createEditRequestDTO.getId()
            );
    }

    @Test
    @DisplayName("Should record an audit trail when edit request is submitted")
    void editRequestSubmissionAuditLog() {
        // Should audit-log who submitted it
    }

    @Test
    @DisplayName("When an edit request has been rejected, the submitter and shared-with users should be notified")
    void rejectedEditRequest() {

    }

    @Test
    @DisplayName("When an edit request has been approved, it should be picked up for processing")
    void approvedEditRequest() {
        // Not sure how this transition works in practice. Perhaps we won't need the PENDING status any more?
    }


    @Test
    @DisplayName("Should not create an edit request with unsafe data in fields")
    void editRequestWithUnsafeData() {
        // Copy and rewrite the existing test to use the `PUT edits/{id}` endpoint instead of the CSV endpoint
    }


}
