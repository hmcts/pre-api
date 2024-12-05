package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestDTOTest {
    private static EditRequest editRequest;

    @BeforeAll
    static void setUp() {
        editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setEditInstruction("{}");
        editRequest.setStatus(EditRequestStatus.COMPLETE);
        editRequest.setStartedAt(Timestamp.from(Instant.now()));
        editRequest.setFinishedAt(Timestamp.from(Instant.now()));
        editRequest.setCreatedAt(Timestamp.from(Instant.now()));
        editRequest.setModifiedAt(Timestamp.from(Instant.now()));

        var user =  new User();
        user.setId(UUID.randomUUID());
        editRequest.setCreatedBy(user);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "123");
        var aCase = HelperFactory.createCase(court, "reference", false, null);
        var booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null);
        var captureSession = HelperFactory.createCaptureSession(
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
        var recording = HelperFactory.createRecording(captureSession, null, 1, "index.mp4", null);
        editRequest.setSourceRecording(recording);
    }

    @Test
    @DisplayName("Should create an edit request dto from edit request entity")
    void testConstructor() {
        var dto = new EditRequestDTO(editRequest);

        assertThat(dto.getId()).isEqualTo(editRequest.getId());
        assertThat(dto.getEditInstruction()).isEqualTo(editRequest.getEditInstruction());
        assertThat(dto.getStatus()).isEqualTo(editRequest.getStatus());
        assertThat(dto.getStartedAt()).isEqualTo(editRequest.getStartedAt());
        assertThat(dto.getFinishedAt()).isEqualTo(editRequest.getFinishedAt());
        assertThat(dto.getCreatedAt()).isEqualTo(editRequest.getCreatedAt());
        assertThat(dto.getModifiedAt()).isEqualTo(editRequest.getModifiedAt());
        assertThat(dto.getCreatedById()).isEqualTo(editRequest.getCreatedBy().getId());
        assertThat(dto.getSourceRecording().getId()).isEqualTo(editRequest.getSourceRecording().getId());
    }
}
