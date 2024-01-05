package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class ShareRecordingDTOTest {

    private static uk.gov.hmcts.reform.preapi.entities.ShareRecording shareRecordingEntity;

    @BeforeAll
    static void setUp() {
        var captureSession = new uk.gov.hmcts.reform.preapi.entities.CaptureSession();
        captureSession.setId(UUID.randomUUID());

        var sharedWithUser = new uk.gov.hmcts.reform.preapi.entities.User();
        sharedWithUser.setId(UUID.randomUUID());
        sharedWithUser.setFirstName("John");
        sharedWithUser.setLastName("Smith");

        var sharedByUser = new uk.gov.hmcts.reform.preapi.entities.User();
        sharedByUser.setId(UUID.randomUUID());
        sharedByUser.setFirstName("Jane");
        sharedByUser.setLastName("Smith");

        shareRecordingEntity = new uk.gov.hmcts.reform.preapi.entities.ShareRecording();
        shareRecordingEntity.setId(UUID.randomUUID());
        shareRecordingEntity.setCaptureSession(captureSession);
        shareRecordingEntity.setSharedBy(sharedByUser);
        shareRecordingEntity.setSharedWith(sharedWithUser);
        shareRecordingEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        shareRecordingEntity.setDeletedAt(null);
    }

    @DisplayName("Should create a shared recording from entity")
    @Test
    void createParticipantFromEntity() {
        var model = new ShareRecordingDTO(shareRecordingEntity);

        assertThat(model.getId()).isEqualTo(shareRecordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(shareRecordingEntity.getCaptureSession().getId());
        assertThat(model.getSharedByUserId()).isEqualTo(shareRecordingEntity.getSharedBy().getId());
        assertThat(model.getSharedWithUserId()).isEqualTo(shareRecordingEntity.getSharedWith().getId());
    }
}
