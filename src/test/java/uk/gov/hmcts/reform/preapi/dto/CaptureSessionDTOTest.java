package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class CaptureSessionDTOTest {

    private static uk.gov.hmcts.reform.preapi.entities.CaptureSession captureSession;

    @BeforeAll
    static void setUp() {
        captureSession = new uk.gov.hmcts.reform.preapi.entities.CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setIngestAddress("ingestAddress");
        captureSession.setFinishedAt(Timestamp.from(java.time.Instant.now()));
        captureSession.setDeletedAt(null);
        captureSession.setLiveOutputUrl("liveOutputUrl");
        captureSession.setStartedAt(Timestamp.from(java.time.Instant.now()));
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var booking = new uk.gov.hmcts.reform.preapi.entities.Booking();
        booking.setId(UUID.randomUUID());
        captureSession.setBooking(booking);

        var user = new uk.gov.hmcts.reform.preapi.entities.User();
        user.setId(UUID.randomUUID());
        captureSession.setFinishedByUser(user);
        captureSession.setStartedByUser(user);
    }

    @DisplayName("Should create a CaptureSession from entity")
    @Test
    void createCaseFromEntity() {
        var model = new CaptureSessionDTO(captureSession);

        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getBookingId()).isEqualTo(captureSession.getBooking().getId());
        assertThat(model.getDeletedAt()).isNull();
    }
}
