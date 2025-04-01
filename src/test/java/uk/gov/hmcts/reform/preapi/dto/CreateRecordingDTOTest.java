package uk.gov.hmcts.reform.preapi.dto;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class CreateRecordingDTOTest {
    private static uk.gov.hmcts.reform.preapi.entities.Recording recordingEntity;

    @BeforeAll
    static void setUp() {
        recordingEntity = new uk.gov.hmcts.reform.preapi.entities.Recording();
        recordingEntity.setId(UUID.randomUUID());
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        recordingEntity.setCaptureSession(captureSession);
        recordingEntity.setVersion(1);
        recordingEntity.setFilename("example-filename.txt");
        recordingEntity.setCreatedAt(Timestamp.from(Instant.now()));
    }

    @DisplayName("Should create a recording from entity")
    @Test
    void createCaseFromEntity() {
        var parentRecording = new uk.gov.hmcts.reform.preapi.entities.Recording();
        parentRecording.setId(UUID.randomUUID());
        recordingEntity.setParentRecording(parentRecording);
        var model = new CreateRecordingDTO(recordingEntity);

        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
        assertThat(model.getParentRecordingId()).isEqualTo(recordingEntity.getParentRecording().getId());
    }

    @DisplayName("Should create a recording from entity when parent recording is null")
    @Test
    void createCaseFromEntityParentRecordingNull() {
        recordingEntity.setParentRecording(null);
        var model = new CreateRecordingDTO(recordingEntity);

        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
        assertThat(model.getParentRecordingId()).isEqualTo(null);
    }

    @Test
    @DisplayName("Should create dto from RecordingDTO")
    void createDtoFromRecordingDto() {
        RecordingDTO recording = new RecordingDTO();
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setParentRecordingId(UUID.randomUUID());
        recording.setVersion(1);
        recording.setFilename("example-filename.txt");
        recording.setCreatedAt(Timestamp.from(Instant.now()));
        recording.setDuration(Duration.ofMinutes(3));
        recording.setEditInstructions("{}");

        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(recording);

        assertThat(createRecordingDTO.getId()).isEqualTo(recording.getId());
        assertThat(createRecordingDTO.getCaptureSessionId()).isEqualTo(recording.getCaptureSession().getId());
        assertThat(createRecordingDTO.getParentRecordingId()).isEqualTo(recording.getParentRecordingId());
        assertThat(createRecordingDTO.getVersion()).isEqualTo(recording.getVersion());
        assertThat(createRecordingDTO.getFilename()).isEqualTo(recording.getFilename());
        assertThat(createRecordingDTO.getDuration()).isEqualTo(recording.getDuration());
        assertThat(createRecordingDTO.getEditInstructions()).isEqualTo(recording.getEditInstructions());
    }
}
