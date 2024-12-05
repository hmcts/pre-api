package uk.gov.hmcts.reform.preapi.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
@SuppressWarnings("PMD - JUnit5TestShouldBePackagePrivate")
class AuditTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Transactional
    public void testSaveAndRetrieveAudit() throws JsonProcessingException {
        Audit audit = new Audit();
        audit.setTableName("TestTable");
        audit.setTableRecordId(UUID.randomUUID());
        audit.setSource(AuditLogSource.PORTAL);
        audit.setCategory("TestCategory");
        audit.setActivity("TestActivity");
        audit.setFunctionalArea("TestFunctionalArea");

        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setCreatedBy(UUID.randomUUID());
        entityManager.persist(audit);
        entityManager.flush();

        Audit retrievedAudit = entityManager.find(Audit.class, audit.getId());

        assertEquals(audit.getId(), retrievedAudit.getId(), "Id should match");
        assertEquals(audit.getTableName(), retrievedAudit.getTableName(), "Table names should match");
        assertEquals(audit.getTableRecordId(), retrievedAudit.getTableRecordId(), "Record ids should match");
        assertEquals(audit.getSource(), retrievedAudit.getSource(), "Source should match");
        assertEquals(audit.getCategory(), retrievedAudit.getCategory(), "Category should match");
        assertEquals(audit.getActivity(), retrievedAudit.getActivity(), "Activity should match");
        assertEquals(audit.getFunctionalArea(), retrievedAudit.getFunctionalArea(), "Functional area should match");
        assertEquals(audit.getDetailsForAudit(), retrievedAudit.getDetailsForAudit(), "Audit details should match");
        assertEquals(audit.getCreatedBy(), retrievedAudit.getCreatedBy(), "Created by should match");
        assertEquals(audit.getCreatedAt(), retrievedAudit.getCreatedAt(), "Created at should match");
    }
}
