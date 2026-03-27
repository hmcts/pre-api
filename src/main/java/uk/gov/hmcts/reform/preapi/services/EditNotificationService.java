package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EditEmailParameters;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;

import static java.lang.String.format;

@Service
@Slf4j
public class EditNotificationService {
    private final EmailServiceFactory emailServiceFactory;
    private final RecordingRepository recordingRepository;

    private final String portalUrl;

    @Autowired
    public EditNotificationService(
        @Value("${portal.url}") String portalUrl,
        final EmailServiceFactory emailServiceFactory,
        final RecordingRepository recordingRepository) {
        this.portalUrl = portalUrl;
        this.emailServiceFactory = emailServiceFactory;
        this.recordingRepository = recordingRepository;
    }

    @Transactional
    public void sendNotifications(Booking booking) {
        booking.getShares()
            .stream()
            .map(ShareBooking::getSharedWith)
            .forEach(u -> emailServiceFactory.getEnabledEmailService().recordingEdited(u, booking.getCaseId()));
    }

    public void editRequestStatusWasUpdated(EditRequest editRequest) {
        Recording sourceRecording = recordingRepository.findById(editRequest.getSourceRecordingId())
            .orElseThrow(() -> new NotFoundException("Unable to send email: could not find source recording "
                                                         + editRequest.getSourceRecordingId()));

        if (sourceRecording.getEditRequest() == null) {
            throw new ResourceInWrongStateException(format(
                "Unable to send email: source recording %s did not have an active edit request",
                sourceRecording.getId()
            ));
        }

        EditEmailParameters editEmailParameters = getEmailParameters(sourceRecording);

        try {
            emailServiceFactory.getEnabledEmailService().sendEmailAboutEditingRequest(editEmailParameters);
        } catch (Exception e) {
            log.error("Error sending email on edit request submission: {}", e.getMessage());
        }
    }

    private @NotNull EditEmailParameters getEmailParameters(Recording outputRecording) {
        EditRequest editRequest = outputRecording.getEditRequest();
        if (editRequest == null) {
            throw new NotFoundException("No edit request found when trying to send notification");
        }

        Booking booking = outputRecording.getCaptureSession().getBooking();
        if (booking == null) {
            throw new NotFoundException("No booking found when trying to send edit notification");
        }

        String courtEmailAddress = booking.getCaseId().getCourt().getGroupEmail();

        if (courtEmailAddress.isBlank()) {
            log.error(
                "Court {} does not have a group email for sending edit request submission email for request: {}",
                booking.getCaseId().getCourt().getName(), outputRecording.getEditRequest().getId()
            );
        }

        String editSummary = generateEditSummary(EditRequestDTO.toDTO(editRequest.getEditCutInstructions()));

        return EditEmailParameters.builder()
            .toEmailAddress(courtEmailAddress)
            .caseReference(booking.getCaseId().getReference())
            .witnessName(booking.getWitnessName())
            .defendantName(booking.getDefendantName())
            .courtName(booking.getCaseId().getCourt().getName())
            .editSummary(editSummary)
            .editRequestStatus(editRequest.getStatus())
            .numberOfRequestedEditInstructions(editRequest.getEditCutInstructions().size())
            .portalURL(portalUrl)
            .jointlyAgreed(editRequest.getJointlyAgreed())
            .rejectionReason(editRequest.getRejectionReason())
            .build();
    }

    private String generateEditSummary(List<EditCutInstructionsDTO> editInstructions) {
        StringJoiner summary = new StringJoiner("");
        for (int i = 0; i < editInstructions.size(); i++) {
            EditCutInstructionsDTO instruction = editInstructions.get(i);
            summary.add(format("Edit %s: %n", i + 1))
                .add(format("Start time: %s%n", instruction.getStartOfCut()))
                .add(format("End time: %s%n", instruction.getEndOfCut()))
                .add(format("Time Removed: %s%n", calculateTimeRemoved(instruction)))
                .add(format("Reason: %s%n%n", instruction.getReason()));
        }

        return summary.toString();
    }

    private String calculateTimeRemoved(EditCutInstructionsDTO instruction) {
        long difference = instruction.getEnd() - instruction.getStart();
        Duration duration = Duration.ofSeconds(difference);

        return format("%02d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }
}
