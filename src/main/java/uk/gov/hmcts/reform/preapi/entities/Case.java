package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
@Entity
@Table(name = "cases")
@SuppressWarnings("PMD.ShortClassName")
public class Case extends CreatedModifiedAtEntity implements ISoftDeletable {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "court_id", referencedColumnName = "id")
    private Court court;

    @Column(name = "reference", nullable = false, length = 25)
    private String reference;

    @Column(name = "test")
    private boolean test;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "origin", nullable = false, columnDefinition = "recording_origin")
    private RecordingOrigin origin;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @OneToMany
    @JoinColumn(name = "case_id", referencedColumnName = "id")
    private Set<Participant> participants;

    @Transient
    private boolean deleted;

    @Transient
    private boolean isSoftDeleteOperation;

    @Column(name = "state", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CaseState state = CaseState.OPEN;

    @Column(name = "closed_at")
    private Timestamp closedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("courtName", court.getName());
        details.put("caseReference", reference);
        details.put("caseParticipants", Stream.ofNullable(getParticipants())
                    .flatMap(participants ->
                                 participants
                                     .stream()
                                     .filter(participant -> participant.getDeletedAt() == null)
                                     .map(Participant::getDetailsForAudit))
                    .collect(Collectors.toSet()));
        details.put("origin", origin);
        details.put("test", test);
        details.put("state", state);
        details.put("closedAt", closedAt);
        return details;
    }

    @Override
    public void setDeleteOperation(boolean deleteOperation) {
        this.isSoftDeleteOperation = deleteOperation;
    }

    @Override
    public boolean isDeleteOperation() {
        return this.isSoftDeleteOperation;
    }
}
