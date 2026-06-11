package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.utils.JsonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EditEmailParametersTest {

    private Recording mockRecording;
    private CaptureSession mockCaptureSession;
    private Booking mockBooking;
    private Court mockCourt;
    private Case mockCase;

    private final UUID editRequestId = UUID.randomUUID();
    private EditRequest editRequest;
    private List<EditCutInstructionDTO> instructionsList;

    private final String portalUrl = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        mockCourt = mock(Court.class);
        when(mockCourt.getName()).thenReturn("Court Name");
        when(mockCourt.getGroupEmail()).thenReturn("Group Email");

        mockCase = mock(Case.class);
        when(mockCase.getCourt()).thenReturn(mockCourt);
        when(mockCase.getReference()).thenReturn("Test Case");

        mockBooking = mock(Booking.class);
        when(mockBooking.getCaseId()).thenReturn(mockCase);
        when(mockBooking.getWitnessName()).thenReturn("Witness Name");
        when(mockBooking.getDefendantName()).thenReturn("Defendant Name");

        mockCaptureSession = mock(CaptureSession.class);
        when(mockCaptureSession.getBooking()).thenReturn(mockBooking);

        mockRecording = mock(Recording.class);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);

        EditCutInstructionDTO instruction1 = new EditCutInstructionDTO(5, 10, "first reason");
        EditCutInstructionDTO instruction2 = new EditCutInstructionDTO(23, 26, "second reason");
        EditCutInstructionDTO instruction3 = new EditCutInstructionDTO(31, 32, "third reason");

        instructionsList = Arrays.asList(instruction1, instruction2, instruction3);

        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(editRequestId);
        dto.setJointlyAgreed(true);
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setRejectionReason("Rejection Reason");

        EditInstructions defaultEditInstructions = new EditInstructions(instructionsList, new ArrayList<>());
        String editInstructionsAsJson = JsonUtils.toJson(defaultEditInstructions);

        editRequest = new EditRequest();
        editRequest.updateEditRequestFromDto(dto, mockRecording, editInstructionsAsJson);
    }

    @Test
    @DisplayName("Cannot create email parameters with null recording")
    void cannotCreateEmailParametersFromNullRecording() {
        editRequest.setSourceRecording(null);

        assertThrows(NotFoundException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters with null capture session")
    void cannotCreateEmailParametersFromNullCaptureSession() {
        when(mockRecording.getCaptureSession()).thenReturn(null);
        assertThrows(NotFoundException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters with null booking")
    void cannotCreateEmailParametersFromNullBooking() {
        when(mockCaptureSession.getBooking()).thenReturn(null);
        assertThrows(NotFoundException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters with null case")
    void cannotCreateEmailParametersFromNullCase() {
        when(mockBooking.getCaseId()).thenReturn(null);
        assertThrows(NotFoundException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters with null court")
    void cannotCreateEmailParametersFromNullCourt() {
        when(mockCase.getCourt()).thenReturn(null);
        assertThrows(NotFoundException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters with blank court email")
    void cannotCreateEmailParametersFromNullCourtEmail() {
        when(mockCourt.getGroupEmail()).thenReturn(" ");
        assertThrows(BadRequestException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters with null edit instructions")
    void cannotCreateEmailParametersFromNullEditInstructions() {
        editRequest.setEditInstruction(null);
        assertThrows(BadRequestException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters if instructions say not to notify")
    void cannotCreateEmailParametersIfInstructionsSayNotToNotify() {
        EditInstructions instructions = new EditInstructions(
            instructionsList, new ArrayList<>(),
            false, false
        );
        editRequest.setEditInstruction(JsonUtils.toJson(instructions));

        assertThrows(BadRequestException.class, () -> new EditEmailParameters(editRequest, portalUrl));
    }

    @Test
    @DisplayName("Cannot create email parameters if portal url is not set")
    void cannotCreateEmailParametersIfPortalUrlIsNotSet() {
        assertThrows(BadRequestException.class, () -> new EditEmailParameters(editRequest, null));
    }

    @Test
    @DisplayName("Should accurately create edit email parameters for REJECTED edit")
    void shouldAccuratelyCreateEmailParameters() {
        editRequest.setStatus(EditRequestStatus.REJECTED);
        editRequest.setRejectionReason("Rejected Reason");
        editRequest.setJointlyAgreed(false);

        EditEmailParameters editEmailParameters = new EditEmailParameters(editRequest, portalUrl);

        assertThat(editEmailParameters.getJointlyAgreed()).isFalse();

        Map<String, Object> paramsMap = editEmailParameters.getEmailParameters();

        assertThat(paramsMap).isNotEmpty();

        assertThat(paramsMap).containsEntry("rejection_reason", editRequest.getRejectionReason());
        assertThat(paramsMap).containsEntry("jointly_agreed", "No");
        assertThat(paramsMap).containsEntry("case_reference", mockCase.getReference());
        assertThat(paramsMap).containsEntry("court_name", mockCourt.getName());
        assertThat(paramsMap).containsEntry("witness_name", "Witness Name");
        assertThat(paramsMap).containsEntry("defendant_names", "Defendant Name");
        assertThat(paramsMap).containsEntry("edit_count", 3);
        assertThat(paramsMap).containsEntry("portal_link", "http://localhost:8080");

        String expectedSummary = """
            Edit 1:\s
            Start time: 00:00:05
            End time: 00:00:10
            Time Removed: 00:00:05
            Reason: first reason

            Edit 2:\s
            Start time: 00:00:23
            End time: 00:00:26
            Time Removed: 00:00:03
            Reason: second reason

            Edit 3:\s
            Start time: 00:00:31
            End time: 00:00:32
            Time Removed: 00:00:01
            Reason: third reason

            """;
        assertThat(paramsMap).containsEntry("edit_summary", expectedSummary);
    }

    @Test
    @DisplayName("Should accurately create edit email parameters for JOINTLY_AGREED edit")
    void shouldAccuratelyCreateEmailParametersForJointlyAgreed() {
        editRequest.setStatus(EditRequestStatus.SUBMITTED);
        editRequest.setRejectionReason(null);
        editRequest.setJointlyAgreed(true);

        EditEmailParameters editEmailParameters = new EditEmailParameters(editRequest, portalUrl);

        assertThat(editEmailParameters.getJointlyAgreed()).isTrue();

        Map<String, Object> paramsMap = editEmailParameters.getEmailParameters();

        assertThat(paramsMap).isNotEmpty();
        assertThat(paramsMap).containsEntry("rejection_reason", "");
        assertThat(paramsMap).containsEntry("jointly_agreed", "Yes");
        assertThat(paramsMap).containsEntry("case_reference", mockCase.getReference());
        assertThat(paramsMap).containsEntry("court_name", mockCourt.getName());
        assertThat(paramsMap).containsEntry("witness_name", "Witness Name");
        assertThat(paramsMap).containsEntry("defendant_names", "Defendant Name");
        assertThat(paramsMap).containsEntry("edit_count", 3);
        assertThat(paramsMap).containsEntry("portal_link", "http://localhost:8080");
        assertThat(paramsMap).containsEntry(
            "edit_summary", """
                Edit 1:\s
                Start time: 00:00:05
                End time: 00:00:10
                Time Removed: 00:00:05
                Reason: first reason

                Edit 2:\s
                Start time: 00:00:23
                End time: 00:00:26
                Time Removed: 00:00:03
                Reason: second reason

                Edit 3:\s
                Start time: 00:00:31
                End time: 00:00:32
                Time Removed: 00:00:01
                Reason: third reason

                """
        );
    }

}
