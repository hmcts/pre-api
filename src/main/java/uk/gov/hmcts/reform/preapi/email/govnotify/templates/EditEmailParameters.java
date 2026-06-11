package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static java.lang.String.format;

@Slf4j
@Getter
public class EditEmailParameters {

    private final Boolean jointlyAgreed;
    private final EditRequestStatus editRequestStatus;
    private final String toEmailAddress; // court group email
    private final Map<String, Object> emailParameters;

    public EditEmailParameters(EditRequest editRequest, String portalUrl) {
        if (portalUrl == null) {
            throw new BadRequestException("Portal URL is missing");
        }

        validateEditRequestIsOkayForNotification(editRequest);

        Booking booking = validateAndRetrieveBooking(editRequest);

        List<EditCutInstructionDTO> requestInstructions = validateAndRetrieveRequestInstructions(editRequest);
        String summary = generateEditSummary(requestInstructions);

        this.emailParameters = Map.of(
            "edit_summary", summary,
            "rejection_reason", editRequest.getRejectionReason() == null ? "" : editRequest.getRejectionReason(),
            "jointly_agreed", editRequest.getJointlyAgreed() ? "Yes" : "No",
            "case_reference", booking.getCaseId().getReference(),
            "court_name", booking.getCaseId().getCourt().getName(),
            "witness_name", booking.getWitnessName(),
            "defendant_names", booking.getDefendantName(),
            "portal_link", portalUrl,
            "edit_count", requestInstructions.size()
        );
        this.toEmailAddress = booking.getCaseId().getCourt().getGroupEmail();
        this.editRequestStatus = editRequest.getStatus();
        this.jointlyAgreed = editRequest.getJointlyAgreed();
    }

    private void validateEditRequestIsOkayForNotification(EditRequest editRequest) {
        if (editRequest == null) {
            throw new NotFoundException("No edit request found when trying to send notification");
        }

        if (editRequest.getSourceRecording() == null) {
            throw new NotFoundException("No recording found when trying to send edit notification, edit ID: "
                                            + editRequest.getId());
        }

        if (editRequest.getSourceRecording().getCaptureSession() == null) {
            throw new NotFoundException("No capture session found when trying to send edit notification, edit ID: "
                                            + editRequest.getId());
        }
    }

    private Booking validateAndRetrieveBooking(EditRequest editRequest) {
        Booking booking = editRequest.getSourceRecording().getCaptureSession().getBooking();
        if (booking == null) {
            throw new NotFoundException("No booking found when trying to send edit notification, edit ID: "
                                            + editRequest.getId());
        }

        if (booking.getCaseId() == null) {
            throw new NotFoundException("No case found when trying to send edit notification, edit ID: "
                                            + editRequest.getId());
        }

        Court court = booking.getCaseId().getCourt();
        if (court == null) {
            throw new NotFoundException("No court found when trying to send edit notification, edit ID: "
                                            + editRequest.getId());
        }

        String courtEmailAddress = court.getGroupEmail();

        if (courtEmailAddress.isBlank()) {
            throw new BadRequestException(
                format(
                    "Court %s does not have a group email for sending edit request submission email"
                        + " for edit ID: %s: ",
                    court.getName(), editRequest.getId()
                ));
        }
        return booking;
    }

    private static List<EditCutInstructionDTO> validateAndRetrieveRequestInstructions(EditRequest editRequest) {
        if (editRequest.getEditInstruction() == null) {
            throw new BadRequestException("No instructions found when trying to send edit notification for edit ID: "
                                              + editRequest.getId());
        }

        EditInstructions instructions = EditInstructions.fromJson(editRequest.getEditInstruction());
        if (instructions == null) {
            throw new BadRequestException("Could not parse instructions for edit request : "
                                              + editRequest.getId());
        }

        if (!instructions.shouldSendNotifications()) {
            throw new BadRequestException("Instructions say not to notify for edit request : "
                                              + editRequest.getId());
        }

        List<EditCutInstructionDTO> requestInstructions = instructions.getRequestedInstructions();

        if (requestInstructions == null || requestInstructions.isEmpty()) {
            throw new BadRequestException("No instructions found when trying to send edit notification for edit ID: "
                                              + editRequest.getId());
        }
        return requestInstructions;
    }

    private String generateEditSummary(List<EditCutInstructionDTO> editInstructions) {
        StringJoiner summary = new StringJoiner("");
        for (int i = 0; i < editInstructions.size(); i++) {
            EditCutInstructionDTO instruction = editInstructions.get(i);
            summary.add(format("Edit %s: %n", i + 1))
                .add(format("Start time: %s%n", instruction.getStartOfCut()))
                .add(format("End time: %s%n", instruction.getEndOfCut()))
                .add(format("Time Removed: %s%n", calculateTimeRemoved(instruction)))
                .add(format("Reason: %s%n%n", instruction.getReason()));
        }

        return summary.toString();
    }

    private String calculateTimeRemoved(EditCutInstructionDTO instruction) {
        long difference = instruction.getEnd() - instruction.getStart();
        Duration duration = Duration.ofSeconds(difference);

        return format("%02d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

}
