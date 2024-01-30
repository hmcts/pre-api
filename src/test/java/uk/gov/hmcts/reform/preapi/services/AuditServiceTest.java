package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuditService.class)
class AuditServiceTest {

    @MockBean
    private AuditRepository auditRepository;

    @Autowired
    private AuditService auditService;

    @DisplayName("Create an audit entry")
    @Test
    void upsertAuditSuccessCreated() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());

        var auditEntity = new Audit();

        when(auditRepository.existsById(auditModel.getId())).thenReturn(false);
        when(auditRepository.save(auditEntity)).thenReturn(auditEntity);

        assertThat(auditService.upsert(auditModel, UUID.randomUUID())).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Create a booking when case not found")
    @Test
    void upsertUpdateAuditAttempt() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());

        when(auditRepository.existsById(auditModel.getId())).thenReturn(true);

        assertThrows(
            ImmutableDataException.class,
            () -> auditService.upsert(auditModel, UUID.randomUUID())
        );
    }
}
