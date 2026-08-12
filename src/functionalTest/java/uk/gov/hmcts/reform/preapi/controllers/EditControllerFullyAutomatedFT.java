package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

// I split this out to capture the first half of the editing process, which doesn't yet have any tests
class EditControllerFullyAutomatedFT extends FunctionalTestBase {
    private static final String EDIT_ENDPOINT = "/edits";

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    private UUID recordingId;
    private RecordingDTO recordingDTO;
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
        recording = new Recording();
        recording.setId(recordingId);
        recording.setCaptureSession(captureSession);
        recording.setDuration(Duration.ofMinutes(30));
        recording.setVersion(1);
        recording.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        recording.setFilename("Test_filename.mp4");

        when(captureSession.getId()).thenReturn(recordingDetails.captureSessionId());
        when(captureSession.getBooking()).thenReturn(booking);

        when(booking.getId()).thenReturn(recordingDetails.bookingId());
        when(booking.getCaseId()).thenReturn(legalCase);

        when(legalCase.getCourt()).thenReturn(mockCourt);
        when(mockCourt.getGroupEmail()).thenReturn("mock@email.com");

        recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true)
                .as(RecordingDTO.class);

        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
                .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
                .thenReturn(recordingDTO.getDuration());
    }

    @Test
    @DisplayName("Should create a DRAFT edit request, update it and submit it. Should be read-only after submission.")
    void editRequestSuccess() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = new CreateEditRequestDTO();
        UUID createEditRequestId = UUID.randomUUID();
        createEditRequestDTO.setId(createEditRequestId);
        createEditRequestDTO.setStatus(EditRequestStatus.DRAFT);
        createEditRequestDTO.setSourceRecordingId(recordingId);

        // Create as DRAFT
        Response createdAsDraft = upsertEditRequestAndGetResponse(createEditRequestId, createEditRequestDTO);
        assertResponseCode(createdAsDraft, 200);
        Assertions.assertThat(createdAsDraft.jsonPath().getString("id"))
                .isEqualTo(createEditRequestId.toString());
        Assertions.assertThat(createdAsDraft.jsonPath().getString("status"))
                .isEqualTo(EditRequestStatus.DRAFT.name());
        Assertions.assertThat(createdAsDraft.jsonPath().getString("source_recording.id"))
                .isEqualTo(createEditRequestDTO.getSourceRecordingId().toString());
        Assertions.assertThat(createdAsDraft.jsonPath().getList("edit_instruction.requestedInstructions"))
                .isEmpty();

        // Update as DRAFT
        List<EditCutInstructionDTO> editInstructions = List.of(EditCutInstructionDTO.builder()
                .startOfCut("00:00:02")
                .endOfCut("00:00:03")
                .build());
        createEditRequestDTO.setEditInstructions(editInstructions);

        Response updatedAsDraft = upsertEditRequestAndGetResponse(createEditRequestId, createEditRequestDTO);
        assertResponseCode(updatedAsDraft, 200);
        Assertions.assertThat(updatedAsDraft.jsonPath().getString("id"))
                .isEqualTo(createEditRequestId.toString());
        Assertions.assertThat(updatedAsDraft.jsonPath().getString("status"))
                .isEqualTo(EditRequestStatus.DRAFT.name());
        Assertions.assertThat(updatedAsDraft.jsonPath().getString("source_recording.id"))
                .isEqualTo(createEditRequestDTO.getSourceRecordingId().toString());

        Assertions.assertThat(updatedAsDraft.jsonPath().getList("edit_instruction.requestedInstructions"))
                .size().isEqualTo(editInstructions.size());
        Assertions.assertThat(updatedAsDraft.jsonPath()
                        .getInt("edit_instruction.requestedInstructions[0].start"))
                .isEqualTo(2);
        Assertions.assertThat(updatedAsDraft.jsonPath()
                        .getInt("edit_instruction.requestedInstructions[0].end"))
                .isEqualTo(3);

        // Submit
        createEditRequestDTO.setStatus(EditRequestStatus.SUBMITTED);
        createEditRequestDTO.setJointlyAgreed(true);
        Response submitted = upsertEditRequestAndGetResponse(createEditRequestId, createEditRequestDTO);
        assertResponseCode(submitted, 200);
        Assertions.assertThat(submitted.jsonPath().getString("id"))
                .isEqualTo(createEditRequestId.toString());
        Assertions.assertThat(submitted.jsonPath().getString("status"))
                .isEqualTo(EditRequestStatus.SUBMITTED.name());

        // Attempt to update edit instructions after submission should fail
        List<EditCutInstructionDTO> updatedEditInstructions = List.of(EditCutInstructionDTO.builder()
                .startOfCut("00:00:06")
                .endOfCut("00:00:07")
                .build());
        createEditRequestDTO.setEditInstructions(updatedEditInstructions);

        Response resubmittedWithChangedInstructions = doPutRequest(
                EDIT_ENDPOINT + "/" + createEditRequestId,
                OBJECT_MAPPER.writeValueAsString(createEditRequestDTO),
                TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(resubmittedWithChangedInstructions, 400);
        Assertions.assertThat(resubmittedWithChangedInstructions.jsonPath().getString("message"))
                .isEqualTo(format(
                        "Cannot alter edit request instructions after submission: "
                                + "edit request %s has status %s",
                        createEditRequestDTO.getId(), createEditRequestDTO.getStatus().toString()
                ));
    }

    @Test
    @DisplayName("Should record an audit trail when edit request is submitted")
    void editRequestSubmissionAuditLog() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = createEditRequestDTO(recordingId);

        // Submit
        createEditRequestDTO.setStatus(EditRequestStatus.SUBMITTED);
        String requestBody = OBJECT_MAPPER.writeValueAsString(createEditRequestDTO);

        Response firstResponse = doPutRequest(
                EDIT_ENDPOINT + "/" + createEditRequestDTO.getId(),
                requestBody,
                TestingSupportRoles.SUPER_USER
        );

        assertResponseCode(firstResponse, 204);

        // TODO: Finish test here when https://tools.hmcts.net/jira/browse/S28-3556 is done
        // Response auditResponse = doGetRequest(AUDIT_ENDPOINT...)
    }

    @Test
    @DisplayName("Should not create an edit request with unsafe data in rejection reason fields")
    void editRequestWithUnsafeDataRejectionReason() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = createEditRequestDTO(recordingId);

        // Submit
        createEditRequestDTO.setStatus(EditRequestStatus.REJECTED);
        createEditRequestDTO.setRejectionReason("this & is unsafe");
        String requestBody = OBJECT_MAPPER.writeValueAsString(createEditRequestDTO);

        Response putResponse = doPutRequest(
                EDIT_ENDPOINT + "/" + createEditRequestDTO.getId(),
                requestBody,
                TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putResponse, 400);

        assertThat(putResponse.getBody().asString()).contains("contains potentially malicious content");
    }

    @Test
    @DisplayName("Should not create an edit request with unsafe data in approved by fields")
    void editRequestWithUnsafeDataApprovedBy() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = createEditRequestDTO(recordingId);

        // Approved
        createEditRequestDTO.setStatus(EditRequestStatus.APPROVED);
        createEditRequestDTO.setRejectionReason(null);
        createEditRequestDTO.setApprovedBy("this & is unsafe");
        String requestBody = OBJECT_MAPPER.writeValueAsString(createEditRequestDTO);

        Response putResponse = doPutRequest(
                EDIT_ENDPOINT + "/" + createEditRequestDTO.getId(),
                requestBody,
                TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putResponse, 400);

        assertThat(putResponse.getBody().asString()).contains("contains potentially malicious content");
    }

    @Test
    @DisplayName("Should not create an edit request with unsafe data in reason fields")
    void editRequestWithUnsafeDataReason() throws JsonProcessingException {
        CreateEditRequestDTO createEditRequestDTO = createEditRequestDTO(recordingId);

        // Approved
        createEditRequestDTO.setStatus(EditRequestStatus.APPROVED);
        List<EditCutInstructionDTO> editInstructions = createEditRequestDTO.getEditInstructions();
        editInstructions.forEach(editInstruction -> {
            editInstruction.setReason("this & is unsafe");
        });
        createEditRequestDTO.setEditInstructions(editInstructions);
        String requestBody = OBJECT_MAPPER.writeValueAsString(createEditRequestDTO);

        Response putResponse = doPutRequest(
                EDIT_ENDPOINT + "/" + createEditRequestDTO.getId(),
                requestBody,
                TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putResponse, 400);

        assertThat(putResponse.getBody().asString()).contains("contains potentially malicious content");
    }

}
