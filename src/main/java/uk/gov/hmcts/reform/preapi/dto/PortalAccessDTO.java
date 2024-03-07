package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "PortalAccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PortalAccessDTO extends CreatePortalAccessDTO {
    @Schema(description = "PortalAccessDeletedAt")
    private Timestamp deletedAt;

    public PortalAccessDTO(PortalAccess access) {
        super(access);
        deletedAt = access.getDeletedAt();
    }
}
