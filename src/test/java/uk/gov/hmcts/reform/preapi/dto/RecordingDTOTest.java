package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordingDTOTest {

    private static Recording recordingEntity;
    private static Case caseEntity;

    @BeforeAll
    static void setUp() {
        caseEntity = HelperFactory.createCase(
            HelperFactory.createCourt(CourtType.CROWN, "Foo Court", null),
            "1234567890",
            false,
            null
        );
        var booking = HelperFactory.createBooking(
            caseEntity,
            Timestamp.from(java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS)),
            null,
            Set.of(
                HelperFactory.createParticipant(caseEntity, ParticipantType.DEFENDANT, "John", "Smith", null),
                HelperFactory.createParticipant(caseEntity, ParticipantType.WITNESS, "Jane", "Smith", null)
            )
        );

        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);

        recordingEntity = new Recording();
        recordingEntity.setId(UUID.randomUUID());
        recordingEntity.setVersion(1);
        recordingEntity.setUrl("http://localhost:8080");
        recordingEntity.setFilename("test.mp4");
        recordingEntity.setCaptureSession(captureSession);
    }

    @DisplayName("Should create a recording from entity")
    @Test
    void createRecordingFromEntity() {
        var model = new RecordingDTO(recordingEntity);

        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getParticipants().size()).isEqualTo(2);
        assertThat(model.getCaseId()).isEqualTo(caseEntity.getId());
        assertThat(model.getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(model.getIsTestCase()).isEqualTo(caseEntity.isTest());
        var sortedList = model
            .getParticipants()
            .stream()
            .sorted(Comparator.comparing(ParticipantDTO::getFirstName))
            .toList();
        assertThat(sortedList.get(0).getFirstName()).isEqualTo("Jane");
        assertThat(sortedList.get(1).getFirstName()).isEqualTo("John");
    }

    @DisplayName("RecordingDTO.participants should be sorted by participant first name")
    @Test
    public void testParticipantSorting() {
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        var aCase = HelperFactory.createCase(new Court(), "1234567890", false, null);
        var booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null);
        var captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        recording.setUrl("http://localhost");
        recording.setFilename("example-filename.txt");
        recording.setCreatedAt(Timestamp.from(Instant.now()));

        booking.setParticipants(Set.of(
            HelperFactory.createParticipant(aCase, ParticipantType.WITNESS, "BBB", "BBB", null),
            HelperFactory.createParticipant(aCase, ParticipantType.DEFENDANT, "CCC", "CCC", null),
            HelperFactory.createParticipant(aCase, ParticipantType.DEFENDANT, "AAA", "AAA", null)
        ));
        var dto = new RecordingDTO(recording);

        var participants = dto.getParticipants();
        assertEquals("AAA", participants.get(0).getFirstName());
        assertEquals("BBB", participants.get(1).getFirstName());
        assertEquals("CCC", participants.get(2).getFirstName());
    }
}
