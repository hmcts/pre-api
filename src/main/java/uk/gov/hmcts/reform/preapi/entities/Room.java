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
@SuppressWarnings("PMD.ShortClassName")
public class Room extends BaseEntity {
    @Column(name = "room", nullable = false, length = 45)
    private String name;

    @ManyToMany(mappedBy = "rooms")
    private Set<Court> courts;
}
