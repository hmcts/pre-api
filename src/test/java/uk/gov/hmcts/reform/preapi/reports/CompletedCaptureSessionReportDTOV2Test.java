package uk.gov.hmcts.reform.preapi.reports;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.dto.reports.CompletedCaptureSessionReportDTOV2;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.preapi.utils.DateTimeUtils.TIME_ZONE;

@Slf4j
@SpringBootTest(classes = {CompletedCaptureSessionReportDTOV2.class})
class CompletedCaptureSessionReportDTOV2Test {

    @Test
    @DisplayName("Should correctly construct object from input object")
    void shouldCorrectlyConstructObjectFromInputObject() {
        // Given
        Court court = new Court();
        court.setName("Test Court");
        court.setCounty("Test County");
        court.setPostcode("TE5 7CO");
        court.setRegions(Set.of(HelperFactory.createRegion("Test Region", Set.of(court))));
        Case caseObj = HelperFactory.createCase(court, "CASE123", true, null);

        Set<Participant> participantsList = new HashSet<>();
        participantsList.add(HelperFactory
                                 .createParticipant(caseObj, ParticipantType.DEFENDANT, "John", "Doe", null));
        participantsList.add(HelperFactory
                                 .createParticipant(caseObj, ParticipantType.DEFENDANT, "Jane", "Smith", null));
        participantsList.add(HelperFactory
                                 .createParticipant(caseObj, ParticipantType.WITNESS, "Alice", "Johnson", null));
        participantsList.add(HelperFactory
                                 .createParticipant(caseObj, ParticipantType.WITNESS, "Bob", "Williams", null));
        participantsList.add(HelperFactory
                                 .createParticipant(caseObj, ParticipantType.WITNESS, "Charlie", "Brown", null));

        ZonedDateTime bookingZonedDateTime = ZonedDateTime.of(
            2026, 9, 3, 7, 0, 0, 0,
            TIME_ZONE
        );

        Timestamp bookingDate = Timestamp.valueOf(bookingZonedDateTime.toLocalDateTime());
        Booking booking = HelperFactory.createBooking(caseObj, bookingDate, null, participantsList);

        ZonedDateTime captureStartTimeZoned = ZonedDateTime.of(
            2026, 9, 4, 12, 3, 0, 0,
            TIME_ZONE);
        Timestamp captureSessionStartedAt = Timestamp.valueOf(captureStartTimeZoned.toLocalDateTime());

        ZonedDateTime captureFinishTimeZoned = ZonedDateTime.of(
            2026, 9, 4,  15, 2, 0, 0,
            TIME_ZONE);

        Timestamp captureSessionFinishedAt = Timestamp.valueOf(
            captureFinishTimeZoned.toLocalDateTime());

        CaptureSession captureSession = HelperFactory.createCaptureSession(
            booking, RecordingOrigin.PRE,
            "ingestUrl", "liveOutputUrl",
            captureSessionStartedAt, mock(User.class), captureSessionFinishedAt, mock(User.class),
            RecordingStatus.RECORDING_AVAILABLE, null
        );
        Recording recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        recording.setDuration(Duration.ofHours(3).plusMinutes(6).plusSeconds(12));

        // When
        CompletedCaptureSessionReportDTOV2 reportDTO = new CompletedCaptureSessionReportDTOV2(recording);

        log.info("Report DTO: {}", reportDTO.getRecordingTime());

        // Then
        assert reportDTO.getCaseReference().equals(caseObj.getReference());
        assert reportDTO.getCourt().equals("Test Court");
        assert reportDTO.getRecordingDate().equals("04/09/2026"); // Matches capture session not booking
//       LER TODO: sort out time zones
//        assert reportDTO.getRecordingTime().equals("12:03:00");
//        assert reportDTO.getFinishTime().equals("15:02:00");
//        assert reportDTO.getDuration().equals("03:06:12");
        assert reportDTO.getScheduledDate().equals("03/09/2026");
        assert reportDTO.getStatus().equals(captureSession.getStatus());
        assert reportDTO.getDefendantNames().contains("John Doe");
        assert reportDTO.getDefendantNames().contains("Jane Smith");
        assert reportDTO.getDefendant() == 2;
        assert reportDTO.getWitnessNames().contains("Alice Johnson");
        assert reportDTO.getWitnessNames().contains("Bob Williams");
        assert reportDTO.getWitnessNames().contains("Charlie Brown");
        assert reportDTO.getWitness() == 3;
    }


}
