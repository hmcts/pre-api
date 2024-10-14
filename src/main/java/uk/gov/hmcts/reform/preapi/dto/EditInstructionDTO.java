package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.opencsv.bean.CsvBindByName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "EditInstructionDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditInstructionDTO {
    @NotNull
    @CsvBindByName(column = "Start")
    @Schema(description = "EditInstructionStart")
    private long start;

    @NotNull
    @CsvBindByName(column = "End")
    @Schema(description = "EditInstructionEnd")
    private long end;
}
