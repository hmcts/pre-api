package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "cases")
public class Case extends BaseEntity {
    @Column(nullable = false, unique = true, length = 16)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "court_id", referencedColumnName = "id", nullable = false)
    private Court court;

    @ManyToMany(mappedBy = "cases")
    private Set<Contact> contacts;
}
