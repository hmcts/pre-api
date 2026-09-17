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

    private final Role mockRole = mock(Role.class);
    private final Court mockCourt = mock(Court.class);

    private final UUID mockRoleId = UUID.randomUUID();

    private CreateAppAccessDTO appAccessToBeUpserted;
    private AppAccess existingAppAccess;

    @BeforeEach
    void setUp() {
        when(mockRole.getId()).thenReturn(mockRoleId);

        UUID mockCourtId = UUID.randomUUID();
        when(mockCourt.getId()).thenReturn(mockCourtId);

        appAccessToBeUpserted = new CreateAppAccessDTO();
        appAccessToBeUpserted.setCourtId(mockCourtId);
        appAccessToBeUpserted.setUserId(UUID.randomUUID());
        appAccessToBeUpserted.setRoleId(mockRoleId);
        appAccessToBeUpserted.setActive(true);

        when(userRepository.findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.of(new User()));
        when(roleRepository.findById(mockRoleId)).thenReturn(Optional.of(mockRole));
        when(courtRepository.findById(mockCourtId)).thenReturn(Optional.of(mockCourt));

        // Default test scenario: app access already exists in database
        existingAppAccess = new AppAccess();
        existingAppAccess.setId(UUID.randomUUID());

        existingAppAccess.setRole(mockRole);
        when(appAccessRepository.findAllByCourtIdAndUserId(mockCourtId, appAccessToBeUpserted.getUserId()))
            .thenReturn(List.of(existingAppAccess));
    }

    @DisplayName("Create an app access entity")
    @Test
    void createAppAccessSuccess() {
        // App access does not exist
        when(appAccessRepository.findAllByCourtIdAndUserId(
            appAccessToBeUpserted.getCourtId(),
            appAccessToBeUpserted.getUserId()
        ))
            .thenReturn(List.of());

        assertThat(underTest.upsert(appAccessToBeUpserted)).isEqualTo(UpsertResult.CREATED);

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(courtRepository, times(1)).findById(appAccessToBeUpserted.getCourtId());
        verify(roleRepository, times(1)).findById(mockRoleId);
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Update an app access entity")
    @Test
    void updateAppAccessSuccess() {
        appAccessToBeUpserted.setActive(false);
        assertThat(underTest.upsert(appAccessToBeUpserted)).isEqualTo(UpsertResult.UPDATED);

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());

        // Not called because access already exists
        verify(roleRepository, times(0)).findById(mockRoleId);
        verify(userRepository, times(0))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(courtRepository, times(0)).findById(appAccessToBeUpserted.getCourtId());

        ArgumentCaptor<AppAccess> appAccessCaptor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(appAccessCaptor.capture());

        assertThat(appAccessCaptor.getValue().isActive()).isEqualTo(false);
    }

    @DisplayName("Should reactivate if app access was previously deleted")
    @Test
    void createAppAccessDeleted() {
        existingAppAccess.setActive(false);
        existingAppAccess.setDeleted(true);
        existingAppAccess.setDeletedAt(Timestamp.from(Instant.now()));

        underTest.upsert(appAccessToBeUpserted);

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        ArgumentCaptor<AppAccess> appAccessCaptor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(appAccessCaptor.capture());
        assertThat(appAccessCaptor.getValue().getDeletedAt()).isNull();
    }

    @DisplayName("Should fail to create/update when user cannot be found")
    @Test
    void createAppAccessUserNotFound() {
        when(appAccessRepository.findAllByCourtIdAndUserId(
            appAccessToBeUpserted.getCourtId(),
            appAccessToBeUpserted.getUserId()
        ))
            .thenReturn(List.of());
        when(userRepository.findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(appAccessToBeUpserted)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: User: " + appAccessToBeUpserted.getUserId());

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when court cannot be found")
    @Test
    void createAppAccessCourtNotFound() {
        when(appAccessRepository.findAllByCourtIdAndUserId(
            appAccessToBeUpserted.getCourtId(),
            appAccessToBeUpserted.getUserId()
        ))
            .thenReturn(List.of());
        when(courtRepository.findById(appAccessToBeUpserted.getCourtId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(appAccessToBeUpserted)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Court: " + appAccessToBeUpserted.getCourtId());

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(appAccessToBeUpserted.getUserId());
        verify(courtRepository, times(1)).findById(appAccessToBeUpserted.getCourtId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when role cannot be found")
    @Test
    void createAppAccessRoleNotFound() {
        when(roleRepository.findById(mockRoleId)).thenReturn(Optional.empty());
        when(appAccessRepository.findAllByCourtIdAndUserId(
            appAccessToBeUpserted.getCourtId(),
            appAccessToBeUpserted.getUserId()
        )).thenReturn(List.of());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(appAccessToBeUpserted)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Role: " + mockRoleId);

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());
        verify(roleRepository, times(1)).findById(mockRoleId);
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
        when(appAccessRepository.findAllByCourtIdAndUserId(
            appAccessToBeUpserted.getCourtId(),
            appAccessToBeUpserted.getUserId()
        )).thenReturn(List.of(existingAppAccess));

        appAccessToBeUpserted.setDefaultCourt(null);

        assertThat(underTest.upsert(appAccessToBeUpserted)).isEqualTo(UpsertResult.UPDATED);

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());

        ArgumentCaptor<AppAccess> captor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().isDefaultCourt()).isTrue();

        verifyNoMoreInteractions(roleRepository);
        verifyNoMoreInteractions(appAccessRepository);
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
    @DisplayName("Should delete by user ID and court ID")
    void deleteByUserIdAndCourtIdSuccess() {
        underTest.deleteByUserIdAndCourtId(appAccessToBeUpserted.getUserId(), appAccessToBeUpserted.getCourtId());

        verify(appAccessRepository, times(1))
            .findAllByCourtIdAndUserId(appAccessToBeUpserted.getCourtId(), appAccessToBeUpserted.getUserId());

        ArgumentCaptor<AppAccess> captor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(1))
            .save(captor.capture());

        AppAccess capturedAccess = captor.getValue();
        assertThat(capturedAccess.getId()).isEqualTo(existingAppAccess.getId());
        assertThat(capturedAccess.getUser()).isEqualTo(existingAppAccess.getUser());
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

    @Test
    @DisplayName("Should be able to reset app access IDs for a user")
    void shouldBeAbleToResetAppAccessIDsForUser() {
        UUID userId = UUID.randomUUID();

        UUID originalId1 = UUID.randomUUID();
        AppAccess access1 = new AppAccess();
        access1.setId(originalId1);

        UUID originalId2 = UUID.randomUUID();
        AppAccess access2 = new AppAccess();
        access2.setId(originalId2);

        when(appAccessRepository.findAllByUserId(userId))
            .thenReturn(List.of(access1, access2));

        underTest.resetAppAccessIDsForUserId(userId);

        verify(appAccessRepository, times(1))
            .findAllByUserId(userId);

        ArgumentCaptor<AppAccess> captor = ArgumentCaptor.forClass(AppAccess.class);
        verify(appAccessRepository, times(2)).save(captor.capture());

        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getId()).isNotIn(originalId1, originalId2);

        verifyNoMoreInteractions(appAccessRepository);
    }

    @Test
    @DisplayName("Should be able to cope if user has no app access when resetting")
    void shouldBeAbleToCopeWhenResettingIfUserHasNoAppAccess() {
        UUID userId = UUID.randomUUID();

        when(appAccessRepository.findAllByUserId(userId))
            .thenReturn(List.of());

        underTest.resetAppAccessIDsForUserId(userId);

        verify(appAccessRepository, times(1)).findAllByUserId(userId);

        verify(appAccessRepository, times(0)).save(any(AppAccess.class));

        verifyNoMoreInteractions(appAccessRepository);
    }
}
