package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "users_terms_conditions")
public class UserTermsAccepted extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "terms_and_conditions_id", nullable = false)
    private TermsAndConditions termsAndConditions;

    @Column(name = "accepted_at", nullable = false)
    private Timestamp acceptedAt;

    @Transient
    private boolean valid;

    public boolean isValid() {
        return acceptedAt.after(
            Timestamp.from(
                Instant.now().minusSeconds(31536000)) // 1 year ago
        );
    }

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", getUser().getId());
        details.put("termsAndConditions", getTermsAndConditions().getId());
        details.put("acceptedAt", getAcceptedAt());
        return details;
    }
}
