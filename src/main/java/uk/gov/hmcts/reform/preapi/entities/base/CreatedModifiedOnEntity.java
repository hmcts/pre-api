package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@MappedSuperclass
@Getter
@Setter
public class CreatedModifiedOnEntity {
    @CreationTimestamp
    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;

    @Column(name = "modified_on")
    private Timestamp modifiedOn;
}
