package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@MappedSuperclass
@Getter
@Setter
public class CreatedModifiedAtEntity extends BaseEntity {
    @CreationTimestamp
    @ColumnTransformer(write = "CAST(? AS TIMESTAMP(3))")
    private Timestamp createdAt;

    @ColumnTransformer(write = "CAST(? AS TIMESTAMP(3))")
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
