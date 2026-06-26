package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "terms_and_conditions")
public class TermsAndConditions extends BaseEntity {
    @NotNull
    @Column(name = "content", nullable = false, length = Integer.MAX_VALUE)
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "terms_and_conditions_type")
    private TermsAndConditionsType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("content", content);
        details.put("type", type);
        return details;
    }
}
