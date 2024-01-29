package uk.gov.hmcts.reform.preapi.listeners;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.AuditAction;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;

import java.util.UUID;


@Component
public class AuditListener {

    @Lazy
    @Autowired
    private AuditRepository auditRepository;

    @Lazy
    @Autowired
    private HttpServletRequest request;

    @Lazy
    @Autowired
    private EntityManager entityManager;

    @PrePersist
    public void postPersist(BaseEntity entity) {
        audit(entity, AuditAction.CREATE);
    }

    @PreUpdate
    public void postUpdate(BaseEntity entity) {
        audit(entity, AuditAction.UPDATE);
    }

    @PreRemove
    public void postRemove(BaseEntity entity) {
        audit(entity, AuditAction.DELETE);
    }

    private void audit(BaseEntity entity, AuditAction action) {
        if (entity.getClass() == Audit.class) {
            return;
        }
        var audit = new Audit();
        audit.setId(UUID.randomUUID());
        audit.setActivity(action.toString());
        audit.setCategory(entity.getClass().getSimpleName());
        audit.setFunctionalArea("API");
        audit.setTableName(getTableName(entityManager, entity.getClass()));
        audit.setTableRecordId(entity.getId());
        audit.setSource(AuditLogSource.AUTO);
        var xUserId = request.getHeader("X-User-Id");
        if (xUserId != null) {
            audit.setCreatedBy(UUID.fromString(xUserId));
        }

        auditRepository.save(audit);
    }

    private static <T> String getTableName(EntityManager em, Class<T> entityClass) {

        Metamodel meta = em.getMetamodel();
        EntityType<T> entityType = meta.entity(entityClass);

        Table t = entityClass.getAnnotation(Table.class);

        return (t == null)
            ? entityType.getName().toUpperCase()
            : t.name();
    }
}
