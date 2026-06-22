package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;

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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PortalAccessService.class)
public class PortalAccessServiceTest {
    @MockitoBean
    private PortalAccessRepository portalAccessRepository;

    @Autowired
    private PortalAccessService underTest;

    @DisplayName("Update a portal access entity")
    @Test
    public void updateSuccess() {
        var model = new CreatePortalAccessDTO();
        model.setId(UUID.randomUUID());
        model.setStatus(AccessStatus.ACTIVE);
        model.setLastAccess(Timestamp.from(Instant.now()));
        model.setInvitedAt(Timestamp.from(Instant.now()));

        var entity = new PortalAccess();
        entity.setId(model.getId());

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.of(entity));

        assertThat(underTest.update(model)).isEqualTo(UpsertResult.UPDATED);

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, times(1)).save(any());
    }

    @DisplayName("Update a portal access entity when portal access does not exist")
    @Test
    public void updateNotFound() {
        var model = new CreatePortalAccessDTO();
        model.setId(UUID.randomUUID());
        model.setStatus(AccessStatus.ACTIVE);
        model.setLastAccess(Timestamp.from(Instant.now()));
        model.setInvitedAt(Timestamp.from(Instant.now()));

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.update(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: PortalAccess: " + model.getId());

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, never()).save(any());
    }

    @DisplayName("Update from inactive to active when registeredAt is null")
    @Test
    void updateFromInactiveToActiveWhenRegisteredAtIsNull() {
        CreatePortalAccessDTO model = new CreatePortalAccessDTO();
        model.setId(UUID.randomUUID());
        model.setStatus(AccessStatus.ACTIVE);
        model.setLastAccess(Timestamp.from(Instant.now()));
        model.setInvitedAt(Timestamp.from(Instant.now()));
        model.setRegisteredAt(null);

        PortalAccess entity = new PortalAccess();
        entity.setId(model.getId());
        entity.setRegisteredAt(null);
        entity.setStatus(AccessStatus.INACTIVE);

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.of(entity));

        assertThat(underTest.update(model)).isEqualTo(UpsertResult.UPDATED);

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, times(1)).save(any());

        assertThat(entity.getStatus()).isEqualTo(AccessStatus.INVITATION_SENT);
        assertThat(entity.getRegisteredAt()).isNull();
    }

    @DisplayName("Update from inactive to active when registeredAt is not null")
    @Test
    void updateFromInactiveToActiveWhenRegisteredAtIsNotNull() {
        CreatePortalAccessDTO model = new CreatePortalAccessDTO();
        Timestamp registeredAt = Timestamp.from(Instant.now());
        model.setId(UUID.randomUUID());
        model.setStatus(AccessStatus.ACTIVE);
        model.setLastAccess(Timestamp.from(Instant.now()));
        model.setInvitedAt(Timestamp.from(Instant.now()));
        model.setRegisteredAt(registeredAt);

        PortalAccess entity = new PortalAccess();
        entity.setId(model.getId());
        entity.setRegisteredAt(registeredAt);
        entity.setStatus(AccessStatus.INACTIVE);

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.of(entity));

        assertThat(underTest.update(model)).isEqualTo(UpsertResult.UPDATED);

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, times(1)).save(any());

        assertThat(entity.getStatus()).isEqualTo(AccessStatus.ACTIVE);
        assertThat(entity.getRegisteredAt()).isEqualTo(registeredAt);
    }

    @DisplayName("Update from active to inactive")
    @Test
    void updateWhenNewStatusIsInactive() {
        CreatePortalAccessDTO model = new CreatePortalAccessDTO();
        Timestamp registeredAt = Timestamp.from(Instant.now());
        model.setId(UUID.randomUUID());
        model.setRegisteredAt(registeredAt);
        model.setStatus(AccessStatus.INACTIVE);

        PortalAccess entity = new PortalAccess();
        entity.setId(model.getId());
        entity.setRegisteredAt(registeredAt);
        entity.setStatus(AccessStatus.ACTIVE);

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.of(entity));

        assertThat(underTest.update(model)).isEqualTo(UpsertResult.UPDATED);

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, times(1)).save(any());

        assertThat(entity.getStatus()).isEqualTo(AccessStatus.INACTIVE);
        assertThat(entity.getRegisteredAt()).isEqualTo(registeredAt);
    }

    @DisplayName("Should mark app access entity as deleted and inactive")
    @Test
    void deleteByIdSuccess() {
        var id = UUID.randomUUID();
        var access = new PortalAccess();
        access.setId(id);
        access.setStatus(AccessStatus.ACTIVE);

        when(portalAccessRepository.findById(id)).thenReturn(Optional.of(access));
        underTest.deleteById(id);

        assertThat(access.getDeletedAt()).isNotNull();
        assertThat(access.getStatus()).isEqualTo(AccessStatus.INACTIVE);

        verify(portalAccessRepository, times(1)).findById(id);
        verify(portalAccessRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should delete by user ID")
    void deleteByUserIdSuccess() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        PortalAccess access = new PortalAccess();
        access.setId(UUID.randomUUID());
        access.setStatus(AccessStatus.ACTIVE);
        access.setUser(user);

        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.of(access));

        underTest.deleteByUserId(userId);

        verify(portalAccessRepository, times(1))
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId);

        ArgumentCaptor<PortalAccess> captor = ArgumentCaptor.forClass(PortalAccess.class);
        verify(portalAccessRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(access.getId());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(AccessStatus.INACTIVE);
    }

    @Test
    @DisplayName("Should cope if deleting by a non-existent user")
    void deleteByNonExistentUser() {
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(any(UUID.class)))
            .thenReturn(Optional.empty());

        underTest.deleteByUserId(UUID.randomUUID());

        verify(portalAccessRepository, times(1))
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(any(UUID.class));

        verifyNoMoreInteractions(portalAccessRepository);
    }

    @Test
    @DisplayName("Should undelete by user ID")
    void undeleteByUserIdSuccess() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        PortalAccess access = new PortalAccess();
        access.setId(UUID.randomUUID());
        access.setStatus(AccessStatus.INACTIVE);
        access.setDeletedAt(Timestamp.from(Instant.now()));
        access.setUser(user);

        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of(access));

        underTest.undeleteByUserId(userId);

        verify(portalAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtIsNotNull(userId);

        ArgumentCaptor<PortalAccess> captor = ArgumentCaptor.forClass(PortalAccess.class);
        verify(portalAccessRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(access.getId());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getDeletedAt()).isNull();

        // Not sure what the status should be after undeleting. This is here for documentation of current behaviour.
        assertThat(captor.getValue().getStatus()).isEqualTo(AccessStatus.INACTIVE);
    }

    @Test
    @DisplayName("Should cope if undeleting a non-existent user")
    void undeleteByNonExistentUser() {
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(any(UUID.class)))
            .thenReturn(List.of());

        underTest.undeleteByUserId(UUID.randomUUID());

        verify(portalAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtIsNotNull(any(UUID.class));

        verifyNoMoreInteractions(portalAccessRepository);
    }

}
