package uk.gov.hmcts.reform.preapi.dto.edit;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.opencsv.bean.CsvBindByName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;

import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "EditCutInstructionsDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditCutInstructionsDTO {
    // CSV also contains these columns:
    // - Edit Number
    // - Total time removed

    @NotNull
    @CsvBindByName(column = "Start time of cut")
    @Schema(description = "EditInstructionStart")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$", message = "must be in format HH:MM:SS")
    private LocalTime startOfCut;

    @NotNull
    @CsvBindByName(column = "End time of cut")
    @Schema(description = "EditInstructionEnd")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$", message = "must be in format HH:MM:SS")
    private LocalTime endOfCut;

    @CsvBindByName(column = "Reason")
    private String reason;

    @Schema(hidden = true)
    private Integer start;

    @Schema(hidden = true)
    private Integer end;


    // Requested instructions are marshalled as JSON into a formatted, human-readable version
    // E.g. "00:01:30"
    // They are converted to integers (ffmpeg-style) for storing in the database

    public Integer getStart() {
        if (start != null) {
            return start;
        }
        start = formatTimeAsInteger(startOfCut);
        return start;
    }

    public Integer getEnd() {
        if (end != null) {
            return end;
        }
        end = formatTimeAsInteger(endOfCut);
        return end;
    }

    public static Integer formatTimeAsInteger(LocalTime localTime) {
        if (localTime == null) {
            return null;
        }
        return localTime.toSecondOfDay();

    }

    public static String formatTimeAsString(Integer time) {
        if (time < 0) {
            throw new IllegalArgumentException("Time in seconds cannot be negative: " + time);
        }

        Integer hours = time / 3600;
        Integer minutes = time % 3600 / 60;
        Integer seconds = time % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static LocalTime formatTimeAsLocalTime(Integer time) {
        String timeString = formatTimeAsString(time);

        return LocalTime.parse(timeString);
    }

    public EditCutInstructionsDTO(Integer start, Integer end, String reason) {
        this.start = start;
        this.end = end;
        this.reason = reason;

        this.startOfCut = formatTimeAsLocalTime(start);
        this.endOfCut = formatTimeAsLocalTime(end);
    }

    public EditCutInstructionsDTO(LocalTime startTime, LocalTime endTime, String reason) {
        this.startOfCut = startTime;
        this.endOfCut = endTime;

        this.start = formatTimeAsInteger(startTime);
        this.end = formatTimeAsInteger(endTime);

        this.reason = reason;
    }

    public EditCutInstructionsDTO(EditCutInstructions editCutInstructions) {
        this.startOfCut = formatTimeAsLocalTime(editCutInstructions.getStart());
        this.endOfCut = formatTimeAsLocalTime(editCutInstructions.getEnd());

        this.start = editCutInstructions.getStart();
        this.end = editCutInstructions.getEnd();

        this.reason = editCutInstructions.getReason();
    }

}
