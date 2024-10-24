package uk.gov.hmcts.reform.preapi.dto.media;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "GenerateAssetDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(value = { "new_recording_id" })
public class GenerateAssetDTO {

    @Schema(description = "GenerateAssetSourceContainer")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-input$")
    private String sourceContainer;

    @Schema(description = "GenerateAssetDestinationContainer")
    private UUID destinationContainer;

    @Schema(description = "GenerateAssetTempAsset")
    private String tempAsset;

    @Schema(description = "GenerateAssetFinalAsset")
    private String finalAsset;

    @Schema(description = "GenerateAssetDescription")
    private String description;

    @Schema(description = "ParentRecordingId")
    private UUID parentRecordingId;


    public UUID getNewRecordingId() {
        return destinationContainer;
    }
}
