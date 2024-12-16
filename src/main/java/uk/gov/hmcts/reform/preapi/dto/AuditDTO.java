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

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "AuditDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuditDTO {

    @Schema(description = "AuditId")
    @NotNull
    private UUID id;

    @Schema(description = "AuditTableName")
    private String tableName;

    @Schema(description = "AuditTableNameRecordId")
    private UUID tableRecordId;

    @Schema(description = "AuditLogSource")
    @NotNull
    private AuditLogSource source;

    @Schema(
        description = "AuditCategory",
        examples = {"User", "Password", "Login", "2FA Code", "Recording", "Livestream"}
    )
    private String category;

    @Schema(description = "AuditActivity", examples = {"Create", "Update", "Delete", "Check", "Play", "Locked"})
    private String activity;

    @Schema(description = "AuditFunctionalArea", examples = {"Registration", "Login", "Video Player", "API", "Admin"})
    private String functionalArea;

    @Schema(description = "AuditDetailsJSONString")
    @JsonRawValue
    private JsonNode auditDetails;

    @Schema(description = "AuditCreatedAt")
    @NotNull
    private Timestamp createdAt;

    @Schema(description = "AuditCreatedBy")
    @NotNull
    private UUID createdBy;

    public AuditDTO(Audit auditEntity) {
        this.id = auditEntity.getId();
        this.tableName = auditEntity.getTableName();
        this.tableRecordId = auditEntity.getTableRecordId();
        this.source = auditEntity.getSource();
        this.category = auditEntity.getCategory();
        this.activity = auditEntity.getActivity();
        this.functionalArea = auditEntity.getFunctionalArea();
        this.auditDetails = auditEntity.getAuditDetails();
        this.createdAt = auditEntity.getCreatedAt();
        this.createdBy = auditEntity.getCreatedBy();
    }
}
