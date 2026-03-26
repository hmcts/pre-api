package uk.gov.hmcts.reform.preapi.dto;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class CreateRecordingDTOTest {
    private static RecordingDTO recordingDTO;

    @BeforeAll
    static void setUp() {
        recordingDTO = new RecordingDTO();
        recordingDTO.setId(UUID.randomUUID());
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recordingDTO.setCaptureSession(captureSession);
        recordingDTO.setVersion(1);
        recordingDTO.setFilename("example-filename.txt");
        recordingDTO.setCreatedAt(Timestamp.from(Instant.now()));
    }

    @DisplayName("Should create a recording from entity when parent recording is null")
    @Test
    void createCaseFromEntityParentRecordingNull() {
        recordingDTO.setParentRecordingId(null);
        var model = new CreateRecordingDTO(recordingDTO);

        assertThat(model.getId()).isEqualTo(recordingDTO.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingDTO.getCaptureSession().getId());
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

    @Test
    @DisplayName("Should create dto with edit request details from RecordingDTO")
    void createDtoWithEditDetailsFromRecordingDto() {
        RecordingDTO recording = new RecordingDTO();
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());

        EditRequestDTO editRequest = new EditRequestDTO();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.REJECTED);
        editRequest.setRejectionReason("I didn't like it");
        EditCutInstructions instructions = new EditCutInstructions(UUID.randomUUID(), 10, 20, "reason");
        editRequest.setEditInstructions(List.of(new EditCutInstructionsDTO(instructions)));

        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setEditRequest(editRequest);

        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(recording);
        assertThat(createRecordingDTO.getId()).isEqualTo(recording.getId());
        assertThat(createRecordingDTO.getEditInstructions()).isEqualTo(recording.getEditInstructions());
        assertThat(createRecordingDTO.getEditRequest()).isEqualTo(recording.getEditRequest());
    }
}
