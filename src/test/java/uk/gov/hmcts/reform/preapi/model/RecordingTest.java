package uk.gov.hmcts.reform.preapi.model;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
public class RecordingTest {
    private static uk.gov.hmcts.reform.preapi.entities.Recording recordingEntity;

    @BeforeAll
    static void setUp() {
        recordingEntity = new uk.gov.hmcts.reform.preapi.entities.Recording();
        recordingEntity.setId(UUID.randomUUID());
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        recordingEntity.setCaptureSession(captureSession);
        recordingEntity.setVersion(1);
        recordingEntity.setUrl("http://localhost");
        recordingEntity.setFilename("example-filename.txt");
        recordingEntity.setCreatedAt(Timestamp.from(Instant.now()));
    }

    @DisplayName("Should create a recording from entity")
    @Test
    void createCaseFromEntity() {
        var parentRecording = new uk.gov.hmcts.reform.preapi.entities.Recording();
        parentRecording.setId(UUID.randomUUID());
        recordingEntity.setParentRecording(parentRecording);
        var model = new Recording(recordingEntity);

        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
        assertThat(model.getParentRecordingId()).isEqualTo(recordingEntity.getParentRecording().getId());
    }

    @DisplayName("Should create a recording from entity when parent recording is null")
    @Test
    void createCaseFromEntityParentRecordingNull() {
        recordingEntity.setParentRecording(null);
        var model = new Recording(recordingEntity);

        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
        assertThat(model.getParentRecordingId()).isEqualTo(null);
    }
}
