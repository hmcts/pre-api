package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigratedItemGroup {
    private CreateCaseDTO acase;
    private CreateBookingDTO booking;
    private CreateCaptureSessionDTO captureSession;
    private CreateRecordingDTO recording;
    private Set<CreateParticipantDTO> participants;
    private PassItem passItem;

    @Override
    public String toString() {
        return "MigratedItemGroup{"
            + "case=" + (acase != null ? acase.toString() : "null")
            + ", booking=" + (booking != null ? booking.toString() : "null")
            + ", captureSession=" + (captureSession != null ? captureSession.toString() : "null")
            + ", recording=" + (recording != null ? recording.toString() : "null")
            + ", participants=" + (participants != null ? participants.toString() : "null")
            + ", passItem=" + (passItem != null ? passItem.toString() : "null")
            + '}';
    }

    public CreateCaseDTO getCase() {
        return acase;
    }
}
