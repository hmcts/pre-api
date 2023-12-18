package uk.gov.hmcts.reform.preapi.dto.base;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.UUID;

@Data
@NoArgsConstructor
public abstract class BaseRecordingDTO {
    @Schema(description = "RecordingId")
    protected UUID id;

    @Schema(description = "RecordingCaptureSessionId")
    protected UUID captureSessionId;

    @Schema(description = "RecordingParentRecordingId")
    protected UUID parentRecordingId;

    @Schema(description = "RecordingVersion")
    protected Integer version;

    @Schema(description = "RecordingURL")
    protected String url; // is this not needed now as it's different for every user?

    @Schema(description = "RecordingFilename")
    protected String filename;

    @Schema(description = "RecordingDuration")
    protected Duration duration;

    @Schema(description = "RecordingEditInstructions")
    protected String editInstructions;
}
