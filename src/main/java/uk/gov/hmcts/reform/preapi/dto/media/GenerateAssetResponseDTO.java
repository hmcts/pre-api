package uk.gov.hmcts.reform.preapi.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "GenerateAssetResponseDTO")
public class GenerateAssetResponseDTO {

    @Schema(description = "GenerateAssetResponseAsset")
    private String asset;

    @Schema(description = "GenerateAssetResponseContainer")
    private String container;

    @Schema(description = "GenerateAssetResponseDescription")
    private String description;

    @Schema(description = "GenerateAssetResponseJobStatus")
    private String jobStatus;
}
