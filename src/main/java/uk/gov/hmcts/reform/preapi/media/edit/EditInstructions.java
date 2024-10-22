package uk.gov.hmcts.reform.preapi.media.edit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;

import java.util.List;

@Getter
@AllArgsConstructor
public class EditInstructions {
    private final List<EditCutInstructionDTO> requestedInstructions;
    private final List<FfmpegEditInstructionDTO> ffmpegInstructions;
}
