package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "rooms")
public class Room extends BaseEntity {
    @Column(unique = true, nullable = false, length = 16)
    private String name;

    @ManyToMany(mappedBy = "rooms")
    private Set<Court> courts;
}
