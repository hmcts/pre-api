package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AppAccessService.class)
class AppAccessServiceTest {
    @MockitoBean
    private AppAccessRepository appAccessRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @Autowired
    private AppAccessService underTest;

    private CreateAppAccessDTO appAccessToBeUpserted;
    private AppAccess existingAppAccess;

    @BeforeEach
    void setUp() {
        appAccessToBeUpserted = new CreateAppAccessDTO();
        appAccessToBeUpserted.setCourtId(UUID.randomUUID());
        appAccessToBeUpserted.setUserId(UUID.randomUUID());
        appAccessToBeUpserted.setRoleId(UUID.randomUUID());
        appAccessToBeUpserted.setActive(true);

        when(userRepository.findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.of(new User()));
        when(roleRepository.findById(appAccessToBeUpserted.getRoleId())).thenReturn(Optional.of(new Role()));
        when(courtRepository.findById(appAccessToBeUpserted.getCourtId())).thenReturn(Optional.of(new Court()));

        // Default test scenario: app access already exists in database
        existingAppAccess = new AppAccess();
        existingAppAccess.setId(UUID.randomUUID());
        when(appAccessRepository.findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(),
                                                          appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.of(existingAppAccess));
    }

    @DisplayName("Create an app access entity")
    @Test
    void createAppAccessSuccess() {
        // App access does not exist
        when(appAccessRepository.findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(),
                                                          appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.empty());

        assertThat(underTest.upsert(appAccessToBeUpserted)).isEqualTo(UpsertResult.CREATED);

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(courtRepository, times(1)).findById(appAccessToBeUpserted.getCourtId());
        verify(roleRepository, times(1)).findById(appAccessToBeUpserted.getRoleId());
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Update an app access entity")
    @Test
    void updateAppAccessSuccess() {
        UUID newRoleId = UUID.randomUUID();
        appAccessToBeUpserted.setRoleId(newRoleId);

        Role newRole = new Role();
        newRole.setId(newRoleId);
        when(roleRepository.findById(newRoleId)).thenReturn(Optional.of(newRole));

        assertThat(underTest.upsert(appAccessToBeUpserted)).isEqualTo(UpsertResult.UPDATED);

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(roleRepository, times(1)).findById(appAccessToBeUpserted.getRoleId());

        // Not called because access already exists
        verify(userRepository, times(0))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(courtRepository, times(0)).findById(appAccessToBeUpserted.getCourtId());

        ArgumentCaptor<AppAccess> appAccessCaptor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(appAccessCaptor.capture());

        assertThat(appAccessCaptor.getValue().getRole().getId()).isEqualTo(newRoleId);
    }

    @DisplayName("Should reactivate if app access was previously deleted")
    @Test
    void createAppAccessDeleted() {
        existingAppAccess.setActive(false);
        existingAppAccess.setDeleted(true);
        existingAppAccess.setDeletedAt(Timestamp.from(Instant.now()));

        underTest.upsert(appAccessToBeUpserted);

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        ArgumentCaptor<AppAccess> appAccessCaptor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(appAccessCaptor.capture());
        assertThat(appAccessCaptor.getValue().getDeletedAt()).isNull();
    }

    @DisplayName("Should fail to create/update when user cannot be found")
    @Test
    void createAppAccessUserNotFound() {
        when(appAccessRepository.findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(),
                                                          appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(appAccessToBeUpserted)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: User: " + appAccessToBeUpserted.getUserId());

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when court cannot be found")
    @Test
    void createAppAccessCourtNotFound() {
        when(appAccessRepository.findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(),
                                                          appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.empty());
        when(courtRepository.findById(appAccessToBeUpserted.getCourtId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(appAccessToBeUpserted)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Court: " + appAccessToBeUpserted.getCourtId());

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(courtRepository, times(1)).findById(appAccessToBeUpserted.getCourtId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when role cannot be found")
    @Test
    void createAppAccessRoleNotFound() {
        when(roleRepository.findById(appAccessToBeUpserted.getRoleId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(appAccessToBeUpserted)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Role: " + appAccessToBeUpserted.getRoleId());

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(roleRepository, times(1)).findById(appAccessToBeUpserted.getRoleId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should mark app access entity as deleted and inactive")
    @Test
    void deleteByIdSuccess() {
        var id = UUID.randomUUID();
        var access = new AppAccess();
        access.setId(id);
        access.setActive(true);

        when(appAccessRepository.findById(id)).thenReturn(Optional.of(access));

        underTest.deleteById(id);

        assertThat(access.getDeletedAt()).isNotNull();
        assertFalse(access.isActive());

        verify(appAccessRepository, times(1)).findById(id);
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Should automatically set court access type to primary when null")
    @Test
    void upsertSetCourtAccessType() {
        var appAccess = new AppAccess();

        when(appAccessRepository.findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(),
                                                          appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.of(appAccess));

        assertThat(underTest.upsert(appAccessToBeUpserted)).isEqualTo(UpsertResult.UPDATED);

        verify(appAccessRepository, times(1))
            .findByCourtIdIsAndUserIs(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(roleRepository, times(1)).findById(appAccessToBeUpserted.getRoleId());

        ArgumentCaptor<AppAccess> captor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().isDefaultCourt()).isTrue();
    }

    @Test
    @DisplayName("Should delete by user ID")
    void deleteByUserIdSuccess() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        AppAccess access = new AppAccess();
        access.setId(UUID.randomUUID());
        access.setActive(true);
        access.setUser(user);

        when(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(List.of(access));

        underTest.deleteByUserId(userId);

        verify(appAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId);

        ArgumentCaptor<AppAccess> captor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(captor.capture());
        AppAccess capturedAccess = captor.getValue();
        assertThat(capturedAccess.getId()).isEqualTo(access.getId());
        assertThat(capturedAccess.getUser()).isEqualTo(user);
        assertThat(capturedAccess.getDeletedAt()).isNotNull();
        assertThat(capturedAccess.isActive()).isFalse();

        verifyNoMoreInteractions(appAccessRepository);

    }

    @Test
    @DisplayName("Should cope if deleting by a non-existent user")
    void deleteByNonExistentUser() {
        when(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(any(UUID.class)))
            .thenReturn(List.of());

        underTest.deleteByUserId(UUID.randomUUID());

        verify(appAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(any(UUID.class));

        verifyNoMoreInteractions(appAccessRepository);
    }

    @Test
    @DisplayName("Should undelete by user ID")
    void undeleteByUserIdSuccess() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        AppAccess access = new AppAccess();
        access.setId(UUID.randomUUID());
        access.setActive(false);
        access.setDeletedAt(Timestamp.from(Instant.now()));
        access.setUser(user);

        when(appAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of(access));

        underTest.undeleteByUserId(userId);

        verify(appAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtIsNotNull(userId);

        ArgumentCaptor<AppAccess> captor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(access.getId());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getDeletedAt()).isNull();
        assertThat(captor.getValue().isActive()).isTrue();

        verifyNoMoreInteractions(appAccessRepository);
    }

    @Test
    @DisplayName("Should cope if undeleting a non-existent user")
    void undeleteByNonExistentUser() {
        when(appAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(any(UUID.class)))
            .thenReturn(List.of());

        underTest.undeleteByUserId(UUID.randomUUID());

        verify(appAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtIsNotNull(any(UUID.class));

        verifyNoMoreInteractions(appAccessRepository);
    }
}
