package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestServiceIT extends IntegrationTestBase {
    @Autowired
    private EditRequestService editRequestService;

    @Test
    @Transactional
    @DisplayName("Should mark previous edits for a recording as deleted")
    void clearPreviousEdits() {
        mockAdminUser();

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        Case caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        Booking booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        CaptureSession captureSession = HelperFactory.createCaptureSession(
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
        entityManager.persist(captureSession);

        Recording parentRecording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        parentRecording.setDuration(Duration.ofMinutes(30));
        entityManager.persist(parentRecording);

        Timestamp recordingV2DeletedAt = Timestamp.from(Instant.now());
        Recording recordingV2 = HelperFactory.createRecording(
            captureSession,
            parentRecording,
            2,
            "filename",
            recordingV2DeletedAt
        );
        recordingV2.setDuration(Duration.ofMinutes(3));
        entityManager.persist(recordingV2);

        Recording recordingV3 = HelperFactory.createRecording(captureSession, parentRecording, 3, "filename", null);
        recordingV3.setDuration(Duration.ofMinutes(3));
        entityManager.persist(recordingV3);

        Recording recordingV4 = HelperFactory.createRecording(captureSession, parentRecording, 4, "filename", null);
        recordingV4.setDuration(Duration.ofMinutes(3));
        entityManager.persist(recordingV4);

        editRequestService.clearPreviousEdits(parentRecording.getId());

        entityManager.refresh(recordingV2);
        assertThat(recordingV2.getDeletedAt()).isNotNull();
        assertThat(recordingV2.getDeletedAt().toInstant().getEpochSecond())
            .isEqualTo(recordingV2DeletedAt.toInstant().getEpochSecond());
        entityManager.refresh(recordingV3);
        assertThat(recordingV3.getDeletedAt()).isNotNull();
        entityManager.refresh(recordingV4);
        assertThat(recordingV4.getDeletedAt()).isNotNull();
    }
}
