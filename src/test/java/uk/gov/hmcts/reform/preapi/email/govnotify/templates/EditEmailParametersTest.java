package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditEmailParameters.class)
public class EditEmailParametersTest {

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private CaptureSession mockCaptureSession;

    @MockitoBean
    private Booking mockBooking;

    @MockitoBean
    private Court mockCourt;

    @MockitoBean
    private Case mockCase;

    private final UUID editRequestId = UUID.randomUUID();
    private EditRequest editRequest;
    private List<EditCutInstructionDTO> instructionsList;

    private final String portalUrl = "http://localhost:8080";

    @BeforeEach
    public void setUp() {
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);
        when(mockCaptureSession.getBooking()).thenReturn(mockBooking);

        when(mockCourt.getName()).thenReturn("Court Name");
        when(mockCourt.getGroupEmail()).thenReturn("Group Email");

        when(mockCase.getCourt()).thenReturn(mockCourt);
        when(mockCase.getReference()).thenReturn("Test Case");

        when(mockBooking.getCaseId()).thenReturn(mockCase);
        when(mockBooking.getWitnessName()).thenReturn("Witness Name");
        when(mockBooking.getDefendantName()).thenReturn("Defendant Name");

        EditCutInstructionDTO instruction1 = new EditCutInstructionDTO(5, 10, "first reason");
        EditCutInstructionDTO instruction2 = new EditCutInstructionDTO(23, 26, "second reason");
        EditCutInstructionDTO instruction3 = new EditCutInstructionDTO(31, 32, "third reason");

        instructionsList = Arrays.asList(instruction1, instruction2, instruction3);

        EditInstructions defaultEditInstructions = new EditInstructions(instructionsList, new ArrayList<>());
        String editInstructionsAsJson = JsonUtils.toJson(defaultEditInstructions);

        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(editRequestId);
        dto.setJointlyAgreed(true);
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setRejectionReason("Rejection Reason");

        editRequest = new EditRequest();
        editRequest.updateEditRequestFromDto(dto, mockRecording, editInstructionsAsJson);
    }

//    @Test
//    @DisplayName("Cannot create email parameters from null edit request")
//    void cannotCreateEmailParametersFromNullEditRequest() {
//        EditRequest editRequest = null;
//        assertThrows(NotFoundException.class, () -> new EditEmailParameters(editRequest));
//    }

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
    public void shouldAccuratelyCreateEmailParameters() {
        editRequest.setStatus(EditRequestStatus.REJECTED);
        editRequest.setRejectionReason("Rejected Reason");
        editRequest.setJointlyAgreed(false);

        EditEmailParameters editEmailParameters = new EditEmailParameters(editRequest, portalUrl);

        assertThat(editEmailParameters.getJointlyAgreed()).isEqualTo(false);

        Map<String, Object> paramsMap = editEmailParameters.getEmailParameters();

        assertThat(paramsMap).isNotNull();
        assertThat(paramsMap).isNotEmpty();
        assertThat(paramsMap.get("rejection_reason")).isEqualTo(editRequest.getRejectionReason());
        assertThat(paramsMap.get("jointly_agreed")).isEqualTo("No");
        assertThat(paramsMap.get("case_reference")).isEqualTo(mockCase.getReference());
        assertThat(paramsMap.get("court_name")).isEqualTo(mockCourt.getName());
        assertThat(paramsMap.get("witness_name")).isEqualTo("Witness Name");
        assertThat(paramsMap.get("defendant_names")).isEqualTo("Defendant Name");
        assertThat(paramsMap.get("edit_count")).isEqualTo(3);
        assertThat(paramsMap.get("portal_link")).isEqualTo("http://localhost:8080");

        Object gotSummary = paramsMap.get("edit_summary");
        assertThat(gotSummary).isNotNull();
        assertThat(gotSummary).isInstanceOf(String.class);

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

        assertThat(gotSummary).isEqualTo(expectedSummary);
    }

    @Test
    @DisplayName("Should accurately create edit email parameters for JOINTLY_AGREED edit")
    public void shouldAccuratelyCreateEmailParametersForJointlyAgreed() {
        editRequest.setStatus(EditRequestStatus.SUBMITTED);
        editRequest.setRejectionReason(null);
        editRequest.setJointlyAgreed(true);

        EditEmailParameters editEmailParameters = new EditEmailParameters(editRequest, portalUrl);

        assertThat(editEmailParameters.getJointlyAgreed()).isEqualTo(true);

        Map<String, Object> paramsMap = editEmailParameters.getEmailParameters();

        assertThat(paramsMap).isNotNull();
        assertThat(paramsMap).isNotEmpty();
        assertThat(paramsMap.get("rejection_reason")).isEqualTo(null);
        assertThat(paramsMap.get("jointly_agreed")).isEqualTo("Yes");
        assertThat(paramsMap.get("case_reference")).isEqualTo(mockCase.getReference());
        assertThat(paramsMap.get("court_name")).isEqualTo(mockCourt.getName());
        assertThat(paramsMap.get("witness_name")).isEqualTo("Witness Name");
        assertThat(paramsMap.get("defendant_names")).isEqualTo("Defendant Name");
        assertThat(paramsMap.get("edit_count")).isEqualTo(3);
        assertThat(paramsMap.get("portal_link")).isEqualTo("test.portal.url");
        assertThat(paramsMap.get("edit_summary")).isEqualTo("""
                                                                Edit 1:
                                                                Start time: 00:00:05
                                                                End time: 00:00:10
                                                                Time Removed: 00:00:05
                                                                Reason: first reason

                                                                Edit 2:
                                                                Start time: 00:00:23
                                                                End time: 00:00:26
                                                                Time Removed: 00:00:03
                                                                Reason: second reason

                                                                Edit 3:
                                                                Start time: 00:00:31
                                                                End time: 00:00:32
                                                                Time Removed: 00:00:01
                                                                Reason: third reason

                                                                """);
    }

}
