package uk.gov.hmcts.reform.preapi.dto.media;

import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AssetDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AssetDTO {
    @Schema(description = "AssetName")
    private String name;

    @Schema(description = "AssetDescription")
    private String description;

    @Schema(description = "AssetContainer")
    private String container;

    @Schema(description = "AssetStorageAccountName")
    private String storageAccountName;

    public AssetDTO(AssetInner amsAsset) {
        name = amsAsset.name();
        description = amsAsset.description();
        container = amsAsset.container();
        storageAccountName = amsAsset.storageAccountName();
    }
}
