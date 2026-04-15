package uk.gov.hmcts.reform.preapi.media.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EditInstructions {
    private List<EditCutInstructionDTO> requestedInstructions;
    private List<FfmpegEditInstructionDTO> ffmpegInstructions;
    private boolean forceReencode;

    public EditInstructions(List<EditCutInstructionDTO> requestedInstructions,
                            List<FfmpegEditInstructionDTO> ffmpegInstructions) {
        this(requestedInstructions, ffmpegInstructions, false);
    }

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
