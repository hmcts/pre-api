package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Audit;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AuditDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuditDTO extends CreateAuditDTO {

    @Schema(description = "AuditCreatedAt")
    @NotNull
    private Timestamp createdAt;

    @Schema(description = "AuditCreatedBy")
    @NotNull
    private UUID createdBy;

    public AuditDTO(Audit auditEntity) {
        super();
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
