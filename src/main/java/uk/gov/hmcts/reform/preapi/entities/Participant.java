package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "participants")
public class Participant extends CreatedModifiedAtEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "case_id", referencedColumnName = "id")
    private Case caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, columnDefinition = "participant_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ParticipantType participantType;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @ManyToMany(mappedBy = "participants")
    private Set<Booking> bookings;

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("caseId", caseId.getId());
        details.put("firstName", firstName);
        details.put("lastName", lastName);
        details.put("participantType", participantType);
        details.put("deleted", deletedAt != null);
        return details;
    }
}
