package uk.gov.hmcts.reform.preapi.entities;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.Set;


@Getter
@Setter
@Entity
@Table(name = "courts")
public class Court extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "court_type", nullable = false, columnDefinition = "court_type")
    @Type(PostgreSQLEnumType.class)
    private CourtType courtType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location_code", length = 25)
    private String locationCode;

    @ManyToMany
    @JoinTable(
        name = "court_region",
        joinColumns = @JoinColumn(name = "court_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "region_id", referencedColumnName = "id")
    )
    private Set<Region> regions;
}
