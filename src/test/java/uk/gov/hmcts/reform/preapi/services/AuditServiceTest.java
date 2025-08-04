package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuditService.class)
class AuditServiceTest {

    private static Audit auditEntity;

    @MockitoBean
    private AuditRepository auditRepository;

    @MockitoBean
    private AppAccessRepository appAccessRepository;

    @MockitoBean
    private PortalAccessRepository portalAccessRepository;

    @Autowired
    private AuditService auditService;

    private Timestamp after;
    private Timestamp before;
    private String functionalArea;
    private AuditLogSource source;
    private String userName;
    private UUID courtId;
    private String caseReference;
    private Pageable pageable;
    private Audit audit;

    @BeforeEach
    void setUp() {
        after = Timestamp.valueOf("2024-01-01 00:00:00");
        before = Timestamp.valueOf("2024-12-31 23:59:59");
        functionalArea = "API";
        source = AuditLogSource.AUTO;
        userName = "testUser";
        courtId = UUID.randomUUID();
        caseReference = "CASE123";
        pageable = mock(Pageable.class);
        audit = new Audit();
        audit.setCreatedBy(UUID.randomUUID());
    }

    @Test
    @DisplayName("Create an audit entry")
    void upsertAuditSuccessCreated() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());
        var user = new User();
        user.setId(UUID.randomUUID());
        var appAccess = new AppAccess();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUser(user);

        var tempAuditEntity = new Audit();

        when(auditRepository.existsById(auditModel.getId())).thenReturn(false);
        when(appAccessRepository.findById(appAccess.getId())).thenReturn(Optional.of(appAccess));
        when(auditRepository.save(tempAuditEntity)).thenReturn(tempAuditEntity);

        assertThat(auditService.upsert(auditModel, appAccess.getId())).isEqualTo(UpsertResult.CREATED);
    }

    @Test
    @DisplayName("Create an audit entry")
    void upsertAuditSuccessWhenIdCannotBeFoundCreated() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());

        var id = UUID.randomUUID();

        var tempAuditEntity = new Audit();

        when(auditRepository.existsById(auditModel.getId())).thenReturn(false);
        when(appAccessRepository.findById(id)).thenReturn(Optional.empty());
        when(auditRepository.save(tempAuditEntity)).thenReturn(tempAuditEntity);

        assertThat(auditService.upsert(auditModel, id)).isEqualTo(UpsertResult.CREATED);
    }

    @Test
    @DisplayName("Create a booking when case not found")
    void upsertUpdateAuditAttempt() {
        var auditModel = new CreateAuditDTO();
        auditModel.setId(UUID.randomUUID());

        when(auditRepository.existsById(auditModel.getId())).thenReturn(true);

        assertThrows(
            ImmutableDataException.class,
            () -> auditService.upsert(auditModel, UUID.randomUUID())
        );
    }

    @Test
    @DisplayName("Find a list of audit logs and return a list of models")
    void findAllAuditsSuccess() {
        auditEntity = new Audit();
        auditEntity.setId(UUID.randomUUID());
        auditEntity.setCreatedAt(Timestamp.from(Instant.now()));

        when(
            auditRepository.searchAll(any(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(new PageImpl<>(List.of(auditEntity)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = auditService.findAll(null, null, null, null, null, null, null, null).get().toList();
        assertThat(modelList).hasSize(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(auditEntity.getId());
    }

    @Test
    @DisplayName("Search audits and return a list of models with created by from app access")
    void findAllAuditsFindUserAppAccess() {
        when(auditRepository.searchAll(
            after,
            before,
            functionalArea,
            source,
            userName,
            courtId,
            caseReference,
            pageable
        ))
            .thenReturn(new PageImpl<>(List.of(audit)));

        var user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("test");
        user.setLastName("user");
        user.setEmail("example@example.com");
        user.setPhone("1234567890");
        user.setOrganisation("org");

        var appAccess = new AppAccess();
        appAccess.setId(audit.getCreatedBy());
        appAccess.setUser(user);

        when(appAccessRepository.findById(audit.getCreatedBy()))
            .thenReturn(Optional.of(appAccess));

        var results = auditService.findAll(
            after,
            before,
            functionalArea,
            source,
            userName,
            courtId,
            caseReference,
            pageable
        );

        assertThat(results).isNotNull();
        assertThat(results.getContent()).hasSize(1);
        var userDto = results.getContent().getFirst().getCreatedBy();
        assertThat(userDto).isNotNull();
        assertThat(userDto.getId()).isEqualTo(user.getId());
        assertThat(userDto.getFirstName()).isEqualTo(user.getFirstName());

        verify(appAccessRepository, times(1)).findById(audit.getCreatedBy());
        verify(portalAccessRepository, never()).findById(audit.getCreatedBy());
    }

    @Test
    @DisplayName("Search audits and return a list of models with created by from portal access")
    void findAllAuditsFindUserPortalAccess() {
        when(auditRepository.searchAll(
            after,
            before,
            functionalArea,
            source,
            userName,
            courtId,
            caseReference,
            pageable
        )).thenReturn(new PageImpl<>(List.of(audit)));

        var user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("test");
        user.setLastName("user");
        user.setEmail("example@example.com");
        user.setPhone("1234567890");
        user.setOrganisation("org");

        var portalAccess = new PortalAccess();
        portalAccess.setId(audit.getCreatedBy());
        portalAccess.setUser(user);

        when(appAccessRepository.findById(audit.getCreatedBy()))
            .thenReturn(Optional.empty());

        when(portalAccessRepository.findById(audit.getCreatedBy()))
            .thenReturn(Optional.of(portalAccess));

        var results = auditService.findAll(
            after,
            before,
            functionalArea,
            source,
            userName,
            courtId,
            caseReference,
            pageable
        );

        assertThat(results).isNotNull();
        assertThat(results.getContent()).hasSize(1);
        var userDto = results.getContent().getFirst().getCreatedBy();
        assertThat(userDto).isNotNull();
        assertThat(userDto.getId()).isEqualTo(user.getId());
        assertThat(userDto.getFirstName()).isEqualTo(user.getFirstName());

        verify(appAccessRepository, times(1)).findById(audit.getCreatedBy());
        verify(portalAccessRepository, times(1)).findById(audit.getCreatedBy());
    }

    @Test
    @DisplayName("Search audits and return a list of models with created by not found")
    void findAllAuditsFindUserNotFound() {
        when(auditRepository.searchAll(
            after,
            before,
            functionalArea,
            source,
            userName,
            courtId,
            caseReference,
            pageable
        ))
            .thenReturn(new PageImpl<>(List.of(audit)));

        when(appAccessRepository.findById(audit.getCreatedBy()))
            .thenReturn(Optional.empty());

        when(portalAccessRepository.findById(audit.getCreatedBy()))
            .thenReturn(Optional.empty());

        var results = auditService.findAll(
            after,
            before,
            functionalArea,
            source,
            userName,
            courtId,
            caseReference,
            pageable
        );

        assertThat(results).isNotNull();
        assertThat(results.getContent()).hasSize(1);
        var userDto = results.getContent().getFirst().getCreatedBy();
        assertThat(userDto).isNotNull();
        assertThat(userDto.getId()).isEqualTo(audit.getCreatedBy());
        assertThat(userDto.getFirstName()).isNull();

        verify(appAccessRepository, times(1)).findById(audit.getCreatedBy());
        verify(portalAccessRepository, times(1)).findById(audit.getCreatedBy());
    }
}
