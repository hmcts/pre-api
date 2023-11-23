package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedOnEntity;

@Getter
@Setter
@Entity
@Table(name = "cases")
public class Case extends CreatedModifiedOnEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", referencedColumnName = "id")
    private Court court;

    @Column(name = "case_ref", nullable = false, length = 25)
    private String caseRef;

    @Column
    private boolean test = false;

    @Column
    private boolean deleted = false;
}
