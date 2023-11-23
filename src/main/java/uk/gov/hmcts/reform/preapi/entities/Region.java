package uk.gov.hmcts.reform.preapi.entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "regions")
public class Region extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;

    @ManyToMany(mappedBy = "regions")
    private Set<Court> courts;
}
