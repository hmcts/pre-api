package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordingDTOTest {

    private static Recording recordingEntity;

    @BeforeAll
    static void setUp() {
        var caseEntity = HelperFactory.createCase(
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
        assertThat(model.getParticipants().stream().toList().get(0).getFirstName()).isEqualTo("Jane");
        assertThat(model.getParticipants().stream().toList().get(1).getFirstName()).isEqualTo("John");
    }
}
