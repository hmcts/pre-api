package uk.gov.hmcts.reform.preapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FfmpegEditInstructionDTO {
    private long start;
    private long end;
}
