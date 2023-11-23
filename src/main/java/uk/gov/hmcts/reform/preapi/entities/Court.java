package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.Set;
import java.util.UUID;


@Getter
@Setter
@Entity
@Table(name = "courts")
public class Court {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(unique = true, nullable = false, updatable = false, insertable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "court_type", nullable = false)
    private CourtType courtType;

    @Column(nullable = false)
    private String name;

    @Column(name = "location_code", length = 25)
    private String locationCode;

    @ManyToMany
    @JoinTable(
        name = "court_regions",
        joinColumns = @JoinColumn(name = "court_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "region_id", referencedColumnName = "id")
    )
    private Set<Region> regions;
}
