package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateAppAccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateAppAccessDTO {
    @Schema(description = "AppAccessId")
    @NotNull
    private UUID id;

    @Schema(description = "AppAccessUserId")
    @NotNull
    private UUID userId;

    @Schema(description = "AppAccessCourtId")
    @NotNull
    private UUID courtId;

    @Schema(description = "AppAccessRoleId")
    @NotNull
    private UUID roleId;

    @Schema(description = "AppAccessIsDefaultCourt")
    // TODO Uncomment annotation when app integrated
    // TODO Also update AppAccessService + PrimaryCourtValidator
    // @NotNull
    private Boolean defaultCourt;

    @Schema(description = "AppAccessActive")
    private Boolean active;

    @Schema(description = "AppAccessLastActive")
    private Timestamp lastActive;
}
