package uk.gov.hmcts.reform.preapi.dto.media;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PlaybackDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlaybackDTO {
    @Schema(description = "PlaybackDashUrl")
    private String dashUrl;

    @Schema(description = "PlaybackHlsUrl")
    private String hlsUrl;

    @Schema(description = "PlaybackLicenseUrl")
    private String licenseUrl;

    @Schema(description = "PlaybackToken")
    private String token;
}
