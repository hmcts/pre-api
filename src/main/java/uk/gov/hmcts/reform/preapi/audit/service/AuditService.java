package uk.gov.hmcts.reform.preapi.audit.service;


import java.util.UUID;

import uk.gov.hmcts.reform.preapi.entities.User;

public interface AuditService {

    void recordAudit(UUID auditableId, String type, User user, String action, String source);

}
