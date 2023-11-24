package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.AuditLogType;

@SpringBootTest(classes = Application.class)
public class AuditTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveAudit() {
        Audit audit = new Audit();
        audit.setTableName("TestTable");
        audit.setTableRecordId(UUID.randomUUID());
        audit.setSource(AuditLogSource.portal);
        audit.setType(AuditLogType.create);
        audit.setCategory("TestCategory");
        audit.setActivity("TestActivity");
        audit.setFunctionalArea("TestFunctionalArea");
        audit.setAuditDetails("TestAuditDetails");
        audit.setCreatedBy("TestUser");
        entityManager.persist(audit);
        entityManager.flush();

        Audit retrievedAudit = entityManager.find(Audit.class, audit.getId());

        assertEquals(audit.getId(), retrievedAudit.getId());
        assertEquals(audit.getTableName(), retrievedAudit.getTableName());
        assertEquals(audit.getTableRecordId(), retrievedAudit.getTableRecordId());
        assertEquals(audit.getSource(), retrievedAudit.getSource());
        assertEquals(audit.getType(), retrievedAudit.getType());
        assertEquals(audit.getCategory(), retrievedAudit.getCategory());
        assertEquals(audit.getActivity(), retrievedAudit.getActivity());
        assertEquals(audit.getFunctionalArea(), retrievedAudit.getFunctionalArea());
        assertEquals(audit.getAuditDetails(), retrievedAudit.getAuditDetails());
        assertEquals(audit.getCreatedBy(), retrievedAudit.getCreatedBy());
        assertEquals(audit.getCreatedOn(), retrievedAudit.getCreatedOn());
    }
}
