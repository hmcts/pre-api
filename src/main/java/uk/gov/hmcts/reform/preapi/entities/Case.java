package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;

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
public class Case extends CreatedModifiedAtEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "court_id", referencedColumnName = "id")
    private Court court;

    @Column(name = "reference", nullable = false, length = 25)
    private String reference;

    @Column(name = "test")
    private boolean test;

    @ColumnTransformer(write = "CAST(? AS TIMESTAMP(3))")
    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @OneToMany
    @JoinColumn(name = "case_id", referencedColumnName = "id")
    private Set<Participant> participants;

    @Transient
    private boolean deleted;

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
        details.put("test", test);
        return details;
    }
}
