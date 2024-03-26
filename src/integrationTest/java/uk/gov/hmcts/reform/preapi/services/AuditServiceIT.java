package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.AuditAction;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

public class AuditServiceIT extends IntegrationTestBase {

    @Autowired
    private CourtService courtService;

    @Autowired
    private AuditService auditService;

    @Transactional
    @Test
    public void testInternalAudit() {
        var court = HelperFactory.createCreateCourtDTO(CourtType.CROWN, "Foo Court", "1234");
        courtService.upsert(court);

        var auditResults = auditService.getAuditsByTableRecordId(court.getId());

        System.out.println(auditResults.get(0));
        System.out.println(auditResults.get(1));
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
}
