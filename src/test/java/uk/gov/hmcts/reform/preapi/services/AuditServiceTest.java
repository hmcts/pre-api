package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuditService.class)
class AuditServiceTest {

    @MockitoBean
    private AuditRepository auditRepository;

    @MockitoBean
    private AppAccessRepository appAccessRepository;

    @Autowired
    private AuditService auditService;

    @DisplayName("Create an audit entry")
    @Test
    void upsertAuditSuccessCreated() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());
        var user = new User();
        user.setId(UUID.randomUUID());
        var appAccess = new AppAccess();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUser(user);

        var auditEntity = new Audit();

        when(auditRepository.existsById(auditModel.getId())).thenReturn(false);
        when(appAccessRepository.findById(appAccess.getId())).thenReturn(Optional.of(appAccess));
        when(auditRepository.save(auditEntity)).thenReturn(auditEntity);

        assertThat(auditService.upsert(auditModel, appAccess.getId())).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Create an audit entry")
    @Test
    void upsertAuditSuccessWhenIdCannotBeFoundCreated() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());

        var id = UUID.randomUUID();

        var auditEntity = new Audit();

        when(auditRepository.existsById(auditModel.getId())).thenReturn(false);
        when(appAccessRepository.findById(id)).thenReturn(Optional.empty());
        when(auditRepository.save(auditEntity)).thenReturn(auditEntity);

        assertThat(auditService.upsert(auditModel, id)).isEqualTo(UpsertResult.CREATED);
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
