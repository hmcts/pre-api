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
@Schema(description = "GenerateAssetDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateAssetDTO {

    @Schema(description = "GenerateAssetSourceContainer")
    private String sourceContainer;

    @Schema(description = "GenerateAssetDestinationContainer")
    private String destinationContainer;

    @Schema(description = "GenerateAssetTempAsset")
    private String tempAsset;

    @Schema(description = "GenerateAssetFinalAsset")
    private String finalAsset;

    @Schema(description = "GenerateAssetDescription")
    private String description;
}
