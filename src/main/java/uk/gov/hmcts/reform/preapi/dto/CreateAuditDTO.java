package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "AuditDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateAuditDTO {

    @Schema(description = "AuditId")
    @NotNull
    protected UUID id;

    @Schema(description = "AuditTableName")
    protected String tableName;

    @Schema(description = "AuditTableNameRecordId")
    protected UUID tableRecordId;

    @Schema(description = "AuditLogSource")
    @NotNull
    protected AuditLogSource source;

    @Schema(
        description = "AuditCategory",
        examples = {"User", "Password", "Login", "2FA Code", "Recording", "Livestream"}
    )
    protected String category;

    @Schema(description = "AuditActivity", examples = {"Create", "Update", "Delete", "Check", "Play", "Locked"})
    protected String activity;

    @Schema(description = "AuditFunctionalArea", examples = {"Registration", "Login", "Video Player", "API", "Admin"})
    protected String functionalArea;

    @Schema(description = "AuditDetailsJSONString")
    @JsonRawValue
    protected JsonNode auditDetails;

    public CreateAuditDTO(Audit auditEntity) {
        this.id = auditEntity.getId();
        this.tableName = auditEntity.getTableName();
        this.tableRecordId = auditEntity.getTableRecordId();
        this.source = auditEntity.getSource();
        this.category = auditEntity.getCategory();
        this.activity = auditEntity.getActivity();
        this.functionalArea = auditEntity.getFunctionalArea();
        this.auditDetails = auditEntity.getAuditDetails();
    }
}
