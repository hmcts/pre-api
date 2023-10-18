package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.Set;


@Getter
@Setter
@Entity
@Table(name = "courts")
public class Court extends BaseEntity {
    @Column(unique = true, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private CourtType type;

    @Column(nullable = false)
    private String location;

    @ManyToMany(mappedBy = "courts")
    private Set<User> users;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "courts_rooms",
        joinColumns = @JoinColumn(name = "court_id"),
        inverseJoinColumns = @JoinColumn(name = "room_id")
    )
    private Set<Room> rooms;
}
