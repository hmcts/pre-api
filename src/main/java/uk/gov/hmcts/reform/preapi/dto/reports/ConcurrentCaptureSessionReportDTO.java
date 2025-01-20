package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "ConcurrentCaptureSessionReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ConcurrentCaptureSessionReportDTO extends BaseReportDTO {
    @Schema(description = "CaptureSessionStartDate")
    private String date;

    @Schema(description = "CaptureSessionStartTime")
    private String startTime;

    @Schema(description = "CaptureSessionEndTime")
    private String endTime;

    @Schema(description = "CaptureSessionStartTimezone")
    private String timezone;

    @JsonIgnore
    private Duration duration;

    @JsonProperty("duration")
    @Schema(description = "CaptureSessionDuration", implementation = String.class)
    public String getDurationAsString() {
        if (duration == null) {
            return null;
        }
        return String.format("%02d:%02d:%02d",
                             duration.toHoursPart(),
                             duration.toMinutesPart(),
                             duration.toSecondsPart());
    }

    public ConcurrentCaptureSessionReportDTO(CaptureSession entity) {
        super(entity.getBooking().getCaseId());
        date = DateTimeUtils.formatDate(entity.getStartedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(entity.getStartedAt());
        startTime = DateTimeUtils.formatTime(entity.getStartedAt());

        if (entity.getFinishedAt() != null) {
            endTime = DateTimeUtils.formatTime(entity.getFinishedAt());
            duration = entity.getRecordings().stream().findFirst().map(Recording::getDuration).orElse(null);
        }

        Stream.ofNullable(entity.getRecordings())
            .flatMap(Set::stream)
            .filter(r -> r.getVersion() == 1 && !r.isDeleted())
            .findFirst()
            .ifPresent(r -> duration = r.getDuration());
    }
}
