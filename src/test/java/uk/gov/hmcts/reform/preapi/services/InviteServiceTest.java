package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = InviteService.class)
public class InviteServiceTest {
    private static User portalUserEntity;
    private static User portalUserEntity2;
    private static PortalAccess portalAccessEntity;
    private static PortalAccess portalAccessEntity2;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PortalAccessRepository portalAccessRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private EmailServiceFactory emailServiceFactory;

    @Autowired
    private InviteService inviteService;

    @BeforeAll
    static void setUp() {
        portalUserEntity = new User();
        portalUserEntity.setId(UUID.randomUUID());
        portalUserEntity.setFirstName("Portal");
        portalUserEntity.setLastName("Person");
        portalUserEntity.setEmail("portal@example.com");
        portalUserEntity.setOrganisation("Portal Org");

        portalUserEntity2 = new User();
        portalUserEntity2.setId(UUID.randomUUID());
        portalUserEntity2.setFirstName("Portal");
        portalUserEntity2.setLastName("Person");
        portalUserEntity2.setEmail("portal@example.com");
        portalUserEntity2.setOrganisation("Portal Org");

        portalAccessEntity = new PortalAccess();
        portalAccessEntity.setId(UUID.randomUUID());
        portalAccessEntity.setUser(portalUserEntity);
        portalAccessEntity.setStatus(AccessStatus.INVITATION_SENT);
        portalUserEntity.setPortalAccess(Set.of(portalAccessEntity));

        portalAccessEntity2 = new PortalAccess();
        portalAccessEntity2.setId(UUID.randomUUID());
        portalAccessEntity2.setUser(portalUserEntity2);
        portalAccessEntity2.setStatus(AccessStatus.ACTIVE);
        portalUserEntity.setPortalAccess(Set.of(portalAccessEntity2));
    }

    @BeforeEach
    void reset() {
        portalUserEntity.setDeletedAt(null);
        portalAccessEntity.setDeletedAt(null);
    }

    @DisplayName("Find a invite by user id and return a model")
    @Test
    void findInviteByUserIdSuccess() {
        when(
            portalAccessRepository
                .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(
                    portalUserEntity.getId(), AccessStatus.INVITATION_SENT)
        ).thenReturn(Optional.of(portalAccessEntity));

        var model = inviteService.findByUserId(portalUserEntity.getId());
        assertThat(model.getUserId()).isEqualTo(portalUserEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(portalUserEntity.getFirstName());
    }

    @DisplayName("Find an invite by user id which doesn't exist")
    @Test
    void findInviteByIdNotFound() {
        when(
            portalAccessRepository
                .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(
                    UUID.randomUUID(), AccessStatus.INVITATION_SENT)
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> inviteService.findByUserId(portalUserEntity.getId())
        );

        verify(portalAccessRepository, times(1))
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(
                portalUserEntity.getId(), AccessStatus.INVITATION_SENT);
    }

    @DisplayName("Find all invites and return a list of models")
    @Test
    void findAllInvitesSuccess() {
        when(
            portalAccessRepository.findAllBy(
                null,
                null,
                null,
                null,
                null,
                null
            )
        ).thenReturn(new PageImpl<>(List.of(portalAccessEntity)));

        var models = inviteService.findAllBy(null, null, null, null, null,null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(1);

        assertAllInvites(models);
    }

    @DisplayName("Delete a invite when user id doesn't exist or user's portal access is not invitation sent")
    @Test
    void deleteInviteByIdNotFound() {
        UUID inviteId = UUID.randomUUID();

        when(
            portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(
                inviteId, AccessStatus.INVITATION_SENT)
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> inviteService.deleteByUserId(inviteId)
        );

        verify(portalAccessRepository, times(1))
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(inviteId, AccessStatus.INVITATION_SENT);
        verify(userService, never()).deleteById(portalAccessEntity.getUser().getId());
        verify(userRepository, never()).deleteById(portalAccessEntity.getUser().getId());
    }

    @DisplayName("Upsert user sends email")
    @Test
    void upsertUserSendsEmail() {
        var createInviteDTO = new CreateInviteDTO();
        createInviteDTO.setUserId(portalUserEntity.getId());

        when(userService.upsert(createInviteDTO)).thenReturn(UpsertResult.CREATED);
        when(userRepository.findById(createInviteDTO.getUserId())).thenReturn(Optional.of(portalUserEntity));
        when(emailServiceFactory.isEnabled()).thenReturn(true);
        var mockEmailService = mock(GovNotify.class);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(mockEmailService);

        assertThat(inviteService.upsert(createInviteDTO)).isEqualTo(UpsertResult.CREATED);

        verify(mockEmailService, times(1)).portalInvite(portalUserEntity);
    }

    @DisplayName("Upsert user cant find user")
    @Test
    void upsertUserCantFindUser() {
        var createInviteDTO = new CreateInviteDTO();
        createInviteDTO.setUserId(portalUserEntity.getId());

        when(userService.upsert(createInviteDTO)).thenReturn(UpsertResult.CREATED);
        when(userRepository.findById(createInviteDTO.getUserId())).thenReturn(Optional.empty());
        when(emailServiceFactory.isEnabled()).thenReturn(true);
        var mockEmailService = mock(GovNotify.class);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(mockEmailService);

        assertThrows(
            NotFoundException.class,
            () -> inviteService.upsert(createInviteDTO)
        );

        verify(mockEmailService, never()).portalInvite(portalUserEntity);
    }

    private void assertAllInvites(Page<InviteDTO> models) {
        var invites = models.get().toList();

        for (var invite : invites) {
            assertThat(invite.getUserId()).isNotNull();
            assertThat(invite.getFirstName()).isNotNull();
            assertThat(invite.getLastName()).isNotNull();
        }
    }
}
