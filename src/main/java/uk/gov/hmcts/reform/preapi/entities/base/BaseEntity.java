package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.entities.listeners.AuditListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@EntityListeners(AuditListener.class)
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

    public Map<String, Object> getDetailsForAudit() {
        return new HashMap<>();
    }
}
