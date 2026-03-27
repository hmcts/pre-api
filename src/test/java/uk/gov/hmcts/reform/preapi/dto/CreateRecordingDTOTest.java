package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
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
import static org.mockito.Mockito.when;

public class CreateRecordingDTOTest {

    @Mock
    private static RecordingDTO recordingDTO;

    @Mock
    private static CaptureSessionDTO captureSessionDTO;

    private static final UUID recordingId = UUID.randomUUID();
    private static final UUID editRequestId = UUID.randomUUID();
    private static final UUID captureSessionId = UUID.randomUUID();

    @BeforeAll
    static void setUp() {
        Timestamp setUpTimestamp = Timestamp.from(Instant.now());
        when(captureSessionDTO.getId()).thenReturn(captureSessionId);

        when(recordingDTO.getId()).thenReturn(UUID.randomUUID());
        when(recordingDTO.getCaptureSession()).thenReturn(captureSessionDTO);
        when(recordingDTO.getVersion()).thenReturn(1);
        when(recordingDTO.getFilename()).thenReturn("original filename");
        when(recordingDTO.getCreatedAt()).thenReturn(setUpTimestamp);
        when(recordingDTO.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(recordingDTO.getEditStatus()).thenReturn(EditRequestStatus.PENDING);

        EditRequestDTO editRequest = new EditRequestDTO();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.REJECTED);
        editRequest.setRejectionReason("I didn't like it");
        EditCutInstructions instructions = new EditCutInstructions(UUID.randomUUID(), 10, 20, "reason");
        editRequest.setEditCutInstructions(List.of(new EditCutInstructionsDTO(instructions)));

        when(recordingDTO.getEditRequest()).thenReturn(editRequest);
    }

    @DisplayName("Should create a recording from entity when parent recording is null")
    @Test
    void createCaseFromEntityParentRecordingNull() {
        when(recordingDTO.getParentRecordingId()).thenReturn(null);
        var model = new CreateRecordingDTO(recordingDTO);

        assertThat(model.getId()).isEqualTo(recordingDTO.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingDTO.getCaptureSession().getId());
        assertThat(model.getParentRecordingId()).isEqualTo(null);
    }

    @Test
    @DisplayName("Should create dto from RecordingDTO")
    void createDtoFromRecordingDto() {
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(recordingDTO);

        assertThat(createRecordingDTO.getId()).isEqualTo(recordingId);
        assertThat(createRecordingDTO.getCaptureSessionId()).isEqualTo(recordingDTO.getCaptureSession().getId());
        assertThat(createRecordingDTO.getParentRecordingId()).isEqualTo(recordingDTO.getParentRecordingId());
        assertThat(createRecordingDTO.getVersion()).isEqualTo(recordingDTO.getVersion());
        assertThat(createRecordingDTO.getFilename()).isEqualTo(recordingDTO.getFilename());
        assertThat(createRecordingDTO.getDuration()).isEqualTo(recordingDTO.getDuration());
        assertThat(createRecordingDTO.getEditInstructions()).isEqualTo(recordingDTO.getEditInstructions());
    }

    @Test
    @DisplayName("Should create dto with edit request details from RecordingDTO")
    void createDtoWithEditDetailsFromRecordingDto() {
        when(recordingDTO.getEditInstructions()).thenReturn("edit instructions");
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(recordingDTO);
        assertThat(createRecordingDTO.getId()).isEqualTo(recordingDTO.getId());
        assertThat(createRecordingDTO.getEditInstructions()).isEqualTo(recordingDTO.getEditInstructions());
        assertThat(createRecordingDTO.getEditRequest()).isEqualTo(recordingDTO.getEditRequest());
        assertThat(createRecordingDTO.getEditStatus()).isEqualTo(recordingDTO.getEditStatus());
    }

    @Test
    @DisplayName("Should return new create recording dto with overridden values")
    void createRecordingSuccess() {

        UUID newRecordingId = UUID.randomUUID();
        Integer newVersionNumber = 6;
        String newFileName = "new_filename.mp4";
        CreateRecordingDTO dto = new CreateRecordingDTO(newRecordingId, newFileName, newVersionNumber, recordingDTO);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(newRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(recordingId);
        assertThat(dto.getVersion()).isEqualTo(newVersionNumber);
        assertThat(dto.getFilename()).isEqualTo(newFileName);
    }

}
