package uk.gov.hmcts.reform.preapi.media.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.util.List;

@Getter
@AllArgsConstructor
public class EditInstructions {
    private final List<EditCutInstructionDTO> requestedInstructions;
    private final List<FfmpegEditInstructionDTO> ffmpegInstructions;

    public static EditInstructions fromJson(String editInstructions) {
        if (editInstructions == null) {
            throw new NullPointerException("Cannot create EditInstructions from a null string");
        }
        try {
            return new ObjectMapper().readValue(editInstructions, EditInstructions.class);
        } catch (Exception e) {
            throw new UnknownServerException("Unable to read edit instructions");
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
