package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
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
    private UUID id;

    @Schema(description = "AuditTableName")
    private String tableName;

    @Schema(description = "AuditTableNameRecordId")
    private UUID tableRecordId;

    @Schema(description = "AuditLogSource")
    private AuditLogSource source;

    @Schema(description = "AuditCategory")
    private String category;

    @Schema(description = "AuditActivity")
    private String activity;

    @Schema(description = "AuditFunctionalArea")
    private String functionalArea;

    @Schema(description = "AuditDetailsJSONString")
    @JsonRawValue
    private String auditDetails;

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
