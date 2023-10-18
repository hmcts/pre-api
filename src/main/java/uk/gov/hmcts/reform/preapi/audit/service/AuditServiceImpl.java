package uk.gov.hmcts.reform.preapi.audit.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.audit.repository.AuditRepository;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    @Autowired
    private AuditRepository auditRepository;

    @Transactional
    @Override
    public void recordAudit(UUID auditableId, String type, User user, String action, String source) {
        Audit audit = new Audit();
        audit.setAuditableId(auditableId);
        audit.setType(type);
        audit.setCreatedBy(user.getId());
        audit.setUser(user);
        audit.setAction(action);
        audit.setSource(source);
        audit.setTimestamp(new Timestamp(new Date().getTime()));

        auditRepository.saveAndFlush(audit);
    }
}
