package uk.gov.hmcts.reform.preapi.entities.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;
import uk.gov.hmcts.reform.preapi.enums.AuditAction;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.exception.UnauditableTableException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.UUID;

import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@Component
public class AuditListener {

    @Lazy
    @Autowired
    private AuditRepository auditRepository;

    @Lazy
    @Autowired
    private AppAccessRepository appAccessRepository;

    @Lazy
    @Autowired
    private HttpServletRequest request;

    @PrePersist
    public void prePersist(BaseEntity entity) {
        audit(entity, AuditAction.CREATE);
    }

    @PreUpdate
    public void preUpdate(BaseEntity entity) {
        if (entity instanceof ISoftDeletable && ((ISoftDeletable) entity).isDeleteOperation()) {
            audit(entity, AuditAction.DELETE);
        } else {
            audit(entity, AuditAction.UPDATE);
        }
    }

    @PreRemove
    public void preRemove(BaseEntity entity) {
        audit(entity, AuditAction.DELETE);
    }

    ObjectMapper mapper = new ObjectMapper();

    private void audit(BaseEntity entity, AuditAction action) {
        if (entity.getClass() == Audit.class) {
            return;
        }
        var audit = new Audit();
        audit.setId(UUID.randomUUID());
        audit.setActivity(action.toString());
        audit.setCategory(entity.getClass().getSimpleName());
        audit.setFunctionalArea("API");
        audit.setTableName(getTableName(entity));
        audit.setTableRecordId(entity.getId());
        audit.setSource(AuditLogSource.AUTO);

        audit.setAuditDetails(mapper.valueToTree(entity.getDetailsForAudit()));
        var userId = getUserIdFromRequestHeader();
        if (userId == null) {
            userId = getUserIdFromContext();
        }
        if (userId != null) {
            audit.setCreatedBy(userId);
        }

        auditRepository.save(audit);
    }

    private static String getTableName(BaseEntity entity) {
        var entityClass = entity.getClass();
        Table t = entityClass.getAnnotation(Table.class);
        if (t == null) {
            throw new UnauditableTableException(entityClass.toString());
        }
        return t.name();
    }

    private UUID getUserIdFromRequestHeader() {
        try {
            var xUserId = request.getHeader(X_USER_ID_HEADER);
            return UUID.fromString(xUserId);
        } catch (Exception e) {
            return null;
        }
    }

    protected UUID getUserIdFromContext() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof UserAuthentication auth)) {
            return null;
        }
        return auth.isAppUser()
            ? (auth.getAppAccess() != null ? auth.getAppAccess().getId() : null)
            : (auth.getPortalAccess() != null ? auth.getPortalAccess().getId() : null);
    }
}
