package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.opencsv.bean.CsvBindByName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "EditCutInstructionDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditCutInstructionDTO {
    // CSV also contains these columns:
    // - Edit Number
    // - Total time removed

    @NotNull
    @PositiveOrZero
    @CsvBindByName(column = "Start time of cut")
    @Schema(description = "EditInstructionStart")
    private String startOfCut;

    @NotNull
    @Positive
    @CsvBindByName(column = "End time of cut")
    @Schema(description = "EditInstructionEnd")
    private String endOfCut;

    @CsvBindByName(column = "Reason")
    private String reason;

    private Long start;
    private Long end;

    public long getStart() {
        if (start != null) {
            return start;
        }
        start = parseTime(startOfCut);
        return start;
    }

    public long getEnd() {
        if (end != null) {
            return end;
        }
        end = parseTime(endOfCut);
        return end;
    }

    private static long parseTime(String time) {
        var units = time.split(":");
        int hours = Integer.parseInt(units[0]);
        int minutes = Integer.parseInt(units[1]);
        int seconds = Integer.parseInt(units[2]);

        return hours * 3600L + minutes * 60L + seconds;
    }
}
