package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class EditCutInstructionsTest {

    @Test
    @DisplayName("Should construct from DTO")
    void constructFromDTO() {
        UUID editRequestId = UUID.randomUUID();
        EditCutInstructionsDTO dto = new EditCutInstructionsDTO(editRequestId, "01:01:01", "01:02:02", "reason");
        EditCutInstructions entity = new EditCutInstructions(dto);

        assertThat(entity.getEditRequestId()).isEqualTo(editRequestId);
        assertThat(entity.getStart()).isEqualTo(3661);
        assertThat(entity.getEnd()).isEqualTo(3722);
        assertThat(entity.getReason()).isEqualTo("reason");
    }

    @Test
    @DisplayName("Should construct from split parameters")
    void constructFromSplitParameters() {
        UUID editRequestId = UUID.randomUUID();
        EditCutInstructions entity = new EditCutInstructions(editRequestId, 3, 5, "reason");
        assertThat(entity.getEditRequestId()).isEqualTo(editRequestId);
        assertThat(entity.getStart()).isEqualTo(3);
        assertThat(entity.getEnd()).isEqualTo(5);
        assertThat(entity.getReason()).isEqualTo("reason");
    }
}
