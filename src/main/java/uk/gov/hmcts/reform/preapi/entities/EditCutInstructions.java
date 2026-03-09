package uk.gov.hmcts.reform.preapi.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.util.List;
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

    public EditCutInstructions(UUID editRequestId, EditCutInstructionsDTO editCutInstructions) {
        this.editRequestId = editRequestId;

        this.start = editCutInstructions.getStart();
        this.end = editCutInstructions.getEnd();
        this.reason = editCutInstructions.getReason();
    }

    public static EditCutInstructions fromJson(String jsonString) {
        try {
            return new ObjectMapper().readValue(jsonString, EditCutInstructions.class);
        } catch (Exception e) {
            throw new UnknownServerException("Unable to read edit cut instructions", e);
        }
    }

    public static List tryFromJson(String jsonString) {
        try {
            return new ObjectMapper().readValue(jsonString, List.class);
        } catch (Exception e) {
            return null;
        }
    }
}
