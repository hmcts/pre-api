package uk.gov.hmcts.reform.preapi.dto.base;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.validators.JsonConstraint;

import java.time.Duration;
import java.util.UUID;

@Data
@NoArgsConstructor
public abstract class BaseRecordingDTO {
    @Schema(description = "RecordingId")
    @NotNull(message = "id is required")
    protected UUID id;

    @Schema(description = "RecordingParentRecordingId")
    protected UUID parentRecordingId;

    @Schema(description = "RecordingVersion")
    @NotNull(message = "version is required")
    @Min(1)
    protected Integer version;

    @Schema(description = "RecordingURL")
    protected String url; // is this not needed now as it's different for every user?

    @Schema(description = "RecordingFilename")
    @NotNull(message = "filename is required")
    protected String filename;

    @Schema(
        description = "RecordingDuration",
        implementation = String.class,
        example = "PT3M"
    )
    protected Duration duration;

    @Schema(description = "RecordingEditInstructions", nullable = true)
    @JsonConstraint
    protected String editInstructions;
}
