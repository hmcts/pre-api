package uk.gov.hmcts.reform.preapi.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

@Deprecated
@Getter
@Setter
@AllArgsConstructor
public class EditInstructions extends BaseEntity {

    public static EditInstructions fromJson(String editInstructions) {
        try {
            return new ObjectMapper().readValue(editInstructions, EditInstructions.class);
        } catch (Exception e) {
            throw new UnknownServerException("Unable to read edit instructions", e);
        }
    }

    public static EditInstructions tryFromJson(String editInstructions) {
        try  {
            return new ObjectMapper().readValue(editInstructions, EditInstructions.class);
        } catch (Exception e) {
            return null;
        }
    }
}
