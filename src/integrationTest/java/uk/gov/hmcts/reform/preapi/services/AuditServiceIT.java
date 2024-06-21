package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditAction;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.UUID;

public class AuditServiceIT extends IntegrationTestBase {

    @Autowired
    private CourtService courtService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private AuditService auditService;

    @Transactional
    @Test
    public void testInternalAudit() {
        var court = HelperFactory.createCreateCourtDTO(CourtType.CROWN, "Foo Court", "1234");
        courtService.upsert(court);

        var auditResults = auditService.getAuditsByTableRecordId(court.getId());

        Assertions.assertEquals(2, auditResults.size());
        Assertions.assertEquals(AuditLogSource.AUTO, auditResults.get(0).getSource());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), auditResults.get(1).getActivity()); // S28-2419

        court.setName("Bar Court");
        courtService.upsert(court);

        var updatedResults = auditService.getAuditsByTableRecordId(court.getId());
        Assertions.assertEquals(3, updatedResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), updatedResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), updatedResults.get(1).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), updatedResults.get(2).getActivity());

    }

    @Transactional
    @Test
    public void testDeleteAudit() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseDTO = HelperFactory.createCase(
            court,
            "ref1234",
            true,
            null);
        var caseId = UUID.randomUUID();
        caseDTO.setId(caseId);

        var auditResultsEmpty = auditService.getAuditsByTableRecordId(caseDTO.getId());

        caseService.upsert(new CreateCaseDTO(caseDTO));
        var auditResultsCreated = auditService.getAuditsByTableRecordId(caseDTO.getId());
        caseService.deleteById(caseDTO.getId());

        var auditResults = auditService.getAuditsByTableRecordId(caseDTO.getId());
        Assertions.assertEquals(0, auditResultsEmpty.size());
        Assertions.assertEquals(2, auditResultsCreated.size());
        Assertions.assertEquals(3, auditResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), auditResults.get(1).getActivity());
        Assertions.assertEquals(AuditAction.DELETE.toString(), auditResults.get(2).getActivity());
    }
}
