package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
public class BaseEntity {
    @Id
    @Column(unique = true, nullable = false)
    private UUID id;

    @PrePersist
    public void prePersist() {
        if (getId() == null) {
            setId(UUID.randomUUID());
        }
    }
}
