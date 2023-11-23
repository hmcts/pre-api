package uk.gov.hmcts.reform.preapi.audit.service;

import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.UUID;


public interface AuditService {

    void recordAudit(UUID auditableId, String type, User user, String action, String source);

}
