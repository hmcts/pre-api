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
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;

import java.sql.Timestamp;

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

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Transient
    private boolean deleted;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
