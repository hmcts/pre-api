package uk.gov.hmcts.reform.preapi.media.edit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;

import java.util.List;

@Getter
@AllArgsConstructor
public class EditInstructions {
    protected final List<EditCutInstructionDTO> requestedInstructions;
    protected final List<FfmpegEditInstructionDTO> ffmpegInstructions;
}
