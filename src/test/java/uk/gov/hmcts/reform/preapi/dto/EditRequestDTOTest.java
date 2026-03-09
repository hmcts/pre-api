package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestDTOTest {
    private static EditRequest editRequest;

    @BeforeAll
    static void setUp() {
        editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setEditCutInstructions(List.of());
        editRequest.setStatus(EditRequestStatus.COMPLETE);
        editRequest.setStartedAt(Timestamp.from(Instant.now()));
        editRequest.setFinishedAt(Timestamp.from(Instant.now()));
        editRequest.setCreatedAt(Timestamp.from(Instant.now()));
        editRequest.setModifiedAt(Timestamp.from(Instant.now()));

        var user =  new User();
        user.setId(UUID.randomUUID());
        editRequest.setCreatedBy(user);
        editRequest.setSourceRecordingId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should create an edit request dto from edit request entity")
    void testConstructor() {
        var dto = new EditRequestDTO(editRequest);

        assertThat(dto.getId()).isEqualTo(editRequest.getId());
        assertThat(dto.getEditInstructions()).isEmpty();
        assertThat(dto.getStatus()).isEqualTo(editRequest.getStatus());
        assertThat(dto.getStartedAt()).isEqualTo(editRequest.getStartedAt());
        assertThat(dto.getFinishedAt()).isEqualTo(editRequest.getFinishedAt());
        assertThat(dto.getCreatedAt()).isEqualTo(editRequest.getCreatedAt());
        assertThat(dto.getModifiedAt()).isEqualTo(editRequest.getModifiedAt());
        assertThat(dto.getCreatedById()).isEqualTo(editRequest.getCreatedBy().getId());
        assertThat(dto.getSourceRecordingId()).isEqualTo(editRequest.getSourceRecordingId());
    }

}
