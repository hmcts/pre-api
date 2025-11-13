package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigratedItemGroupTest {
    @Test
    void shouldReturnStringWithNullFieldsInMigratedItemGroup() {
        MigratedItemGroup group = MigratedItemGroup.builder()
            .build();

        String expected = "MigratedItemGroup{case=null, booking=null, captureSession=null, "
            + "recording=null, participants=null, passItem=null}";

        assertEquals(expected, group.toString());
    }

    @Test
    void shouldReturnStringWithPopulatedFieldsInMigratedItemGroup() {
        CreateCaseDTO caseDTO = new CreateCaseDTO();
        CreateBookingDTO bookingDTO = new CreateBookingDTO();
        CreateCaptureSessionDTO captureSessionDTO = new CreateCaptureSessionDTO();
        CreateRecordingDTO recordingDTO = new CreateRecordingDTO();
        CreateParticipantDTO participant = new CreateParticipantDTO();
        PassItem passItem = new PassItem(new ExtractedMetadata(), new  ProcessedRecording());

        MigratedItemGroup group = MigratedItemGroup.builder()
            .acase(caseDTO)
            .booking(bookingDTO)
            .captureSession(captureSessionDTO)
            .recording(recordingDTO)
            .participants(Set.of(participant))
            .passItem(passItem)
            .build();

        String expected = "MigratedItemGroup{case="
            + caseDTO
            + ", booking="
            + bookingDTO
            + ", captureSession="
            + captureSessionDTO
            + ", recording="
            + recordingDTO
            + ", participants=["
            + participant
            + "], passItem="
            + passItem
            + "}";

        assertEquals(expected, group.toString());
    }

    @Test
    void shouldReturnStringWithMixedFieldsInMigratedItemGroup() {
        CreateCaseDTO caseDTO = new CreateCaseDTO();
        CreateRecordingDTO recordingDTO = new CreateRecordingDTO();
        PassItem passItem = new PassItem(new ExtractedMetadata(), new  ProcessedRecording());

        MigratedItemGroup group = MigratedItemGroup.builder()
            .acase(caseDTO)
            .recording(recordingDTO)
            .passItem(passItem)
            .build();

        String expected = "MigratedItemGroup{case="
            + caseDTO
            + ", booking=null, captureSession=null, recording="
            + recordingDTO
            + ", participants=null, passItem="
            + passItem
            + "}";

        assertEquals(expected, group.toString());
    }
}
