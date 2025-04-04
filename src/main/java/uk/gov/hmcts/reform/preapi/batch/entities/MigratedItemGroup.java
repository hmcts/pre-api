package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
public class MigratedItemGroup {
    private CreateCaseDTO acase;
    private CreateBookingDTO booking;
    private CreateCaptureSessionDTO captureSession;
    private CreateRecordingDTO recording;
    private Set<CreateParticipantDTO> participants;
    private List<CreateShareBookingDTO> shareBookings;
    private List<CreateInviteDTO> invites;
    private PassItem passItem;

    @Override
    public String toString() {
        return "MigratedItemGroup{"
            + "case=" + (acase != null ? acase.toString() : "null")
            + ", booking=" + (booking != null ? booking.toString() : "null")
            + ", captureSession=" + (captureSession != null ? captureSession.toString() : "null")
            + ", recording=" + (recording != null ? recording.toString() : "null")
            + ", participants=" + (participants != null ? participants.toString() : "null")
            + ", shareBookings=" + (shareBookings != null ? shareBookings.toString() : "null")
            + ", passItem=" + (passItem != null ? passItem.toString() : "null")
            + '}';
    }

    public CreateCaseDTO getCase() {
        return acase;
    }
}
