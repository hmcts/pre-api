package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Region;

@Data
@NoArgsConstructor
@Schema(description = "RegionDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RegionDTO {
    @Schema(description = "RegionName")
    private String name;

    public RegionDTO(Region regionEntity) {
        this.name = regionEntity.getName();
    }
}
