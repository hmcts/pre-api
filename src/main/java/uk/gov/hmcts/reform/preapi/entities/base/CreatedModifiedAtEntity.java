package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@MappedSuperclass
@Getter
@Setter
public class CreatedModifiedAtEntity extends BaseEntity {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "modified_at")
    private Timestamp modifiedAt;

    @PreUpdate
    public void preUpdate() {
        setModifiedAt(new Timestamp(System.currentTimeMillis()));
    }

    @Override
    public void prePersist() {
        super.prePersist();
        setCreatedAt(new Timestamp(System.currentTimeMillis()));
    }
}
