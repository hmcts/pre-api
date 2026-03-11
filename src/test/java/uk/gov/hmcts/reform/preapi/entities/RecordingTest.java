package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.criteria.CriteriaBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class RecordingTest {

    @Test
    @DisplayName("Should get recording details for audit")
    void getRecordingDetailsForAudit() {
        Recording parentRecording = new Recording();
        parentRecording.setId(UUID.randomUUID());

        Recording recording = new Recording();
        recording.setParentRecording(parentRecording);
        recording.setVersion(3);
        recording.setFilename("test filename");
        recording.setDuration(Duration.ofHours(3));
        recording.setDeletedAt(Timestamp.from(Instant.now()));
        recording.setEditInstruction("edit instruction");

        var audit = recording.getDetailsForAudit();
        assertThat(audit.get("parentRecordingId")).isEqualTo(parentRecording.getId());
        assertThat(audit.get("recordingVersion")).isEqualTo(3);
        assertThat(audit.get("recordingFilename")).isEqualTo("test filename");
        assertThat(audit.get("recordingDuration")).isEqualTo("PT3H");
        assertThat(audit.get("recordingEditInstruction")).isEqualTo("edit instruction");
        assertThat(audit.get("deleted")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should build edit instructions from Java object if available")
    void shouldBuildEditInstructionsFromJavaObjectIfAvailable() {

        EditRequest editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());

        EditCutInstructions first = new EditCutInstructions(
            editRequest.getId(), 30, 50,
            "first edit"
        );
        EditCutInstructions second = new EditCutInstructions(
            editRequest.getId(), 60, 70,
            "second edit"
        );
        editRequest.setEditCutInstructions(List.of(first, second));

        Recording recording = new Recording();
        recording.setEditInstruction("should not be this string: deprecated");
        recording.setEditRequest(editRequest);

        var audit = recording.getDetailsForAudit();
        assertThat(audit.get("recordingEditInstruction")).isEqualTo(format(
            "[{\"id\":null,"
                + "\"editRequestId\":\"%s\",\"start\":30,\"end\":50,"
                + "\"reason\":\"first edit\",\"detailsForAudit\":{}},{\"id\":null,"
                + "\"editRequestId\":\"%s\",\"start\":60,\"end\":70,"
                + "\"reason\":\"second edit\",\"detailsForAudit\":{}}]", editRequest.getId(), editRequest.getId()
        ));
    }

}
