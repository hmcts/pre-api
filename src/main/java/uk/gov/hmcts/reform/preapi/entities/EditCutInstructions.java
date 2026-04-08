package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "edit_cut_instructions")
public class EditCutInstructions extends BaseEntity {

    @NotNull
    @Column(name = "edit_request_id")
    UUID editRequestId;

    @Column(name = "start_edit_seconds")
    Integer start;

    @Column(name = "end_edit_seconds")
    Integer end;

    @Column(name = "reason")
    String reason;

    public EditCutInstructions(UUID editRequestId, Integer start, Integer end, String reason) {
        this.editRequestId = editRequestId;
        this.start = start;
        this.end = end;
        this.reason = reason;
    }

    public EditCutInstructions(EditCutInstructionsDTO editCutInstructions) {
        this.editRequestId = editCutInstructions.getEditRequestId();
        this.start = editCutInstructions.getStart();
        this.end = editCutInstructions.getEnd();
        this.reason = editCutInstructions.getReason();
    }

}
