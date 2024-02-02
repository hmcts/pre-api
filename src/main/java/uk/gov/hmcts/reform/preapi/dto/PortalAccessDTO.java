package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "PortalAccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PortalAccessDTO {
    @Schema(description = "PortalAccessId")
    private UUID id;

    @Schema(description = "PortalAccessLastAccess")
    private Timestamp lastAccess;

    @Schema(description = "PortalAccessStatus")
    private AccessStatus status;

    @Schema(description = "PortalAccessInvitationDateTime")
    private Timestamp invitationDateTime;

    @Schema(description = "PortalAccessRegisteredDateTime")
    private Timestamp registeredDateTime;

    @Schema(description = "PortalAccessDeletedAt")
    private Timestamp deletedAt;

    public PortalAccessDTO(PortalAccess access) {
        id = access.getId();
        lastAccess = access.getLastAccess();
        status = access.getStatus();
        invitationDateTime = access.getInvitationDateTime();
        registeredDateTime = access.getRegisteredDateTime();
        deletedAt = access.getDeletedAt();
    }
}
