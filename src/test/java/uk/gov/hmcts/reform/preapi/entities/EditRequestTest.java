package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.preapi.entities.EditRequest.convertEditCutInstructionsFromJson;

public class EditRequestTest {

    private static final Path sampleEditRequestDeprecatedJsonPath =
        Path.of("src/test/resources/edit-requests/existing/sample-for-edit-request-test.json");

    @Test
    @DisplayName("Should be able to deserialize from old-style JSON into ordered edit cut requests")
    void deserializeDeprecatedJson() throws IOException {
        var dto = new EditRequest();

        String content = Files.readString(sampleEditRequestDeprecatedJsonPath, StandardCharsets.UTF_8);

        List<EditCutInstructions> editCutInstructionsList = convertEditCutInstructionsFromJson(content);
        dto.setEditCutInstructions(editCutInstructionsList);
        assertThat(dto.getEditCutInstructions().size()).isEqualTo(2);

        EditCutInstructions first = dto.getEditCutInstructions().getFirst();
        assertThat(first.getEditRequestId()).isEqualTo(UUID.fromString("f264f9cb-0203-4fa6-9234-b6efec06819e"));
        assertThat(first.getStart()).isEqualTo(180);
        assertThat(first.getEnd()).isEqualTo(300);
        assertThat(first.getReason()).isEqualTo("Removing 2 minutes");

        EditCutInstructions second = dto.getEditCutInstructions().get(1);
        assertThat(second.getEditRequestId()).isEqualTo(UUID.fromString("f264f9cb-0203-4fa6-9234-b6efec06819e"));
        assertThat(second.getStart()).isEqualTo(480);
        assertThat(second.getEnd()).isEqualTo(490);
        assertThat(second.getReason()).isEqualTo("Removing 10 seconds");
    }

    @Test
    @DisplayName("Should get details for audit")
    void getDetailsForAudit() {
        EditRequest editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecordingId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.COMPLETE);
        editRequest.setEditCutInstructions(List.of(
            new EditCutInstructions(editRequest.getId(), 100, 200, "reason")
        ));
        editRequest.setStartedAt(Timestamp.from(Instant.now()));
        editRequest.setFinishedAt(Timestamp.from(Instant.now()));
        editRequest.setCreatedAt(Timestamp.from(Instant.now()));
        editRequest.setModifiedAt(Timestamp.from(Instant.now()));
        editRequest.setJointlyAgreed(false);
        editRequest.setRejectionReason("Test rejection");
        editRequest.setApprovedAt(Timestamp.from(Instant.now()));
        editRequest.setApprovedBy("approver");

        User user = new User();
        user.setId(UUID.randomUUID());
        editRequest.setCreatedBy(user);

        Map<String, Object> detailsForAudit = editRequest.getDetailsForAudit();

        assertThat(detailsForAudit.get("id")).isEqualTo(editRequest.getId());
        assertThat(detailsForAudit.get("editInstructions"))
            .isEqualTo(format(
                "[{\"id\":null,\"editRequestId\":\"%s\",\"start\":100,\"end\":200,\"reason\":\"reason\",\"detailsForAudit\":{}}]",
                editRequest.getId()
            ));
        assertThat(detailsForAudit.get("status")).isEqualTo(editRequest.getStatus());
        assertThat(detailsForAudit.get("startedAt")).isEqualTo(editRequest.getStartedAt());
        assertThat(detailsForAudit.get("finishedAt")).isEqualTo(editRequest.getFinishedAt());
        assertThat(detailsForAudit.get("createdBy")).isEqualTo(editRequest.getCreatedBy().getId());
        assertThat(detailsForAudit.get("rejectionReason")).isEqualTo(editRequest.getRejectionReason());
        assertThat(detailsForAudit.get("jointlyAgreed")).isEqualTo(editRequest.getJointlyAgreed());
        assertThat(detailsForAudit.get("approvedAt")).isEqualTo(editRequest.getApprovedAt());
        assertThat(detailsForAudit.get("approvedBy")).isEqualTo(editRequest.getApprovedBy());
        assertThat(detailsForAudit.get("sourceRecordingId")).isEqualTo(editRequest.getSourceRecordingId());
    }
}
