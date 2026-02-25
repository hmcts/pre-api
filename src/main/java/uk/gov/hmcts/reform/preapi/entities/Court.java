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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
@Entity
@Table(name = "courts")
public class Court extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "court_type", nullable = false, columnDefinition = "court_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CourtType courtType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location_code", length = 25)
    private String locationCode;

    @Column(name = "county")
    private String county;

    @Column(name = "postcode", length = 8)
    private String postcode;

    @Column(name = "group_email", length = 100)
    private String groupEmail;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "court_region",
        joinColumns = @JoinColumn(name = "court_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "region_id", referencedColumnName = "id")
    )
    private Set<Region> regions;

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("courtName", name);
        details.put("courtType", courtType);
        details.put("courtLocationCode", locationCode);
        details.put("courtCounty", county);
        details.put("courtPostcode", postcode);
        details.put("courtGroupEmail", groupEmail);
        details.put("courtRegions", Stream.ofNullable(getRegions())
                    .flatMap(regions -> regions.stream().map(Region::getName))
                    .collect(Collectors.toSet()));
        return details;
    }
}
