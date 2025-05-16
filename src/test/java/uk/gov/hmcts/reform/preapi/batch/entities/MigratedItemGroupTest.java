package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MigratedItemGroupTest {
    @Test
    void toStringWithAllNulls() {
        MigratedItemGroup group = new MigratedItemGroup();
        String result = group.toString();

        assertThat(result)
            .contains("case=null")
            .contains("booking=null")
            .contains("captureSession=null")
            .contains("recording=null")
            .contains("participants=null")
            .contains("passItem=null");
    }

    @Test
    void toStringWithSomeFieldsSet() {
        CreateCaseDTO caseDTO = mock(CreateCaseDTO.class);
        when(caseDTO.toString()).thenReturn("MockedCase");

        CreateParticipantDTO participantDTO = mock(CreateParticipantDTO.class);
        when(participantDTO.toString()).thenReturn("MockedParticipant");

        MigratedItemGroup group = MigratedItemGroup.builder()
            .acase(caseDTO)
            .participants(Set.of(participantDTO))
            .build();

        String result = group.toString();

        assertThat(result)
            .contains("case=MockedCase")
            .contains("participants=[MockedParticipant]")
            .contains("booking=null")
            .contains("captureSession=null")
            .contains("recording=null")
            .contains("passItem=null");
    }

    @Test
    void toStringWithAllFieldsSet() {
        CreateCaseDTO caseDTO = mock(CreateCaseDTO.class);
        CreateBookingDTO bookingDTO = mock(CreateBookingDTO.class);
        CreateCaptureSessionDTO captureDTO = mock(CreateCaptureSessionDTO.class);
        CreateRecordingDTO recordingDTO = mock(CreateRecordingDTO.class);
        CreateParticipantDTO participantDTO = mock(CreateParticipantDTO.class);
        PassItem passItem = mock(PassItem.class);

        when(caseDTO.toString()).thenReturn("CaseDTO");
        when(bookingDTO.toString()).thenReturn("BookingDTO");
        when(captureDTO.toString()).thenReturn("CaptureSessionDTO");
        when(recordingDTO.toString()).thenReturn("RecordingDTO");
        when(participantDTO.toString()).thenReturn("ParticipantDTO");
        when(passItem.toString()).thenReturn("PassItem");

        MigratedItemGroup group = MigratedItemGroup.builder()
            .acase(caseDTO)
            .booking(bookingDTO)
            .captureSession(captureDTO)
            .recording(recordingDTO)
            .participants(Set.of(participantDTO))
            .passItem(passItem)
            .build();

        String result = group.toString();

        assertThat(result)
            .contains("case=CaseDTO")
            .contains("booking=BookingDTO")
            .contains("captureSession=CaptureSessionDTO")
            .contains("recording=RecordingDTO")
            .contains("participants=[ParticipantDTO]")
            .contains("passItem=PassItem");
    }

    @Test
    void getCaseFromACase() {
        CreateCaseDTO caseDTO = mock(CreateCaseDTO.class);
        MigratedItemGroup group = MigratedItemGroup.builder()
            .acase(caseDTO)
            .build();

        assertThat(group.getCase()).isEqualTo(group.getAcase());
    }
}
