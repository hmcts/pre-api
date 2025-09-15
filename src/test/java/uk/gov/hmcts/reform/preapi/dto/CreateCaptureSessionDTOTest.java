package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCaptureSessionDTOTest {
    @Test
    void constructorFromCaptureSessionDTO() {
        CaptureSessionDTO dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(UUID.randomUUID());
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setIngestAddress("ingestAddress");
        dto.setLiveOutputUrl("url");
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setStartedByUserId(UUID.randomUUID());
        dto.setFinishedAt(Timestamp.from(Instant.now()));
        dto.setFinishedByUserId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        CreateCaptureSessionDTO createDto = new CreateCaptureSessionDTO(dto);

        assertThat(createDto.getId()).isEqualTo(dto.getId());
        assertThat(createDto.getBookingId()).isEqualTo(dto.getBookingId());
        assertThat(createDto.getOrigin()).isEqualTo(dto.getOrigin());
        assertThat(createDto.getIngestAddress()).isEqualTo(dto.getIngestAddress());
        assertThat(createDto.getLiveOutputUrl()).isEqualTo(dto.getLiveOutputUrl());
        assertThat(createDto.getStartedAt()).isEqualTo(dto.getStartedAt());
        assertThat(createDto.getStartedByUserId()).isEqualTo(dto.getStartedByUserId());
        assertThat(createDto.getFinishedAt()).isEqualTo(dto.getFinishedAt());
        assertThat(createDto.getFinishedByUserId()).isEqualTo(dto.getFinishedByUserId());
        assertThat(createDto.getStatus()).isEqualTo(dto.getStatus());
    }
}
