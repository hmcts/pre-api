package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.AuditLogType;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class AuditTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSaveAndRetrieveAudit() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
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
        audit.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        entityManager.persist(audit);
        entityManager.flush();

        Audit retrievedAudit = entityManager.find(Audit.class, audit.getId());

        assertEquals(audit.getId(), retrievedAudit.getId(), "Id should match");
        assertEquals(audit.getTableName(), retrievedAudit.getTableName(), "Table names should match");
        assertEquals(audit.getTableRecordId(), retrievedAudit.getTableRecordId(), "Record ids should match");
        assertEquals(audit.getSource(), retrievedAudit.getSource(), "Source should match");
        assertEquals(audit.getType(), retrievedAudit.getType(), "Type should match");
        assertEquals(audit.getCategory(), retrievedAudit.getCategory(), "Category should match");
        assertEquals(audit.getActivity(), retrievedAudit.getActivity(), "Activity should match");
        assertEquals(audit.getFunctionalArea(), retrievedAudit.getFunctionalArea(), "Functional area should match");
        assertEquals(audit.getAuditDetails(), retrievedAudit.getAuditDetails(), "Audit details should match");
        assertEquals(audit.getCreatedBy(), retrievedAudit.getCreatedBy(), "Created by should match");
        assertEquals(audit.getCreatedAt(), retrievedAudit.getCreatedAt(), "Created at should match");
        assertEquals(audit.getUpdatedAt(), retrievedAudit.getUpdatedAt(), "Updated at should match");
        assertEquals(audit.getDeletedAt(), retrievedAudit.getDeletedAt(), "Deleted at should match");
    }
}
