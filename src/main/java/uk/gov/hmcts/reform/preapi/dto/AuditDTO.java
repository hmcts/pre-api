package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "AuditDTO")
@EqualsAndHashCode(callSuper = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AuditDTO extends CreateAuditDTO {

    @NotNull
    @Schema(description = "AuditCreatedAt")
    private Timestamp createdAt;

    @NotNull
    @Schema(description = "AuditCreatedBy")
    private BaseUserDTO createdBy;

    public AuditDTO(Audit auditEntity) {
        super();
        id = auditEntity.getId();
        tableName = auditEntity.getTableName();
        tableRecordId = auditEntity.getTableRecordId();
        source = auditEntity.getSource();
        category = auditEntity.getCategory();
        activity = auditEntity.getActivity();
        functionalArea = auditEntity.getFunctionalArea();
        auditDetails = auditEntity.getAuditDetails();
        createdAt = auditEntity.getCreatedAt();
        if (auditEntity.getCreatedBy() != null) {
            createdBy = new BaseUserDTO();
            createdBy.setId(auditEntity.getCreatedBy());
        }
    }

    public AuditDTO(Audit auditEntity, User user) {
        this(auditEntity);
        createdBy = new BaseUserDTO(user);
    }
}
