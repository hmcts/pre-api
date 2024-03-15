package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AppAccessService.class)
public class AppAccessServiceTest {
    @MockBean
    private AppAccessRepository appAccessRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CourtRepository courtRepository;

    @MockBean
    private RoleRepository roleRepository;

    @Autowired
    private AppAccessService appAccessService;

    @DisplayName("Create an app access entity")
    @Test
    void createAppAccessSuccess() {
        var model = new CreateAppAccessDTO();
        model.setId(UUID.randomUUID());
        model.setCourtId(UUID.randomUUID());
        model.setUserId(UUID.randomUUID());
        model.setRoleId(UUID.randomUUID());

        when(appAccessRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(model.getUserId())).thenReturn(Optional.of(new User()));
        when(roleRepository.findById(model.getRoleId())).thenReturn(Optional.of(new Role()));
        when(courtRepository.findById(model.getCourtId())).thenReturn(Optional.of(new Court()));

        assertThat(appAccessService.upsert(model)).isEqualTo(UpsertResult.CREATED);

        verify(appAccessRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getUserId());
        verify(courtRepository, times(1)).findById(model.getCourtId());
        verify(roleRepository, times(1)).findById(model.getRoleId());
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Update an app access entity")
    @Test
    void updateAppAccessSuccess() {
        var model = new CreateAppAccessDTO();
        model.setId(UUID.randomUUID());
        model.setCourtId(UUID.randomUUID());
        model.setUserId(UUID.randomUUID());
        model.setRoleId(UUID.randomUUID());
        model.setActive(true);

        var entity = new AppAccess();
        entity.setId(model.getId());

        when(appAccessRepository.findById(model.getId())).thenReturn(Optional.of(entity));
        when(userRepository.findByIdAndDeletedAtIsNull(model.getUserId())).thenReturn(Optional.of(new User()));
        when(roleRepository.findById(model.getRoleId())).thenReturn(Optional.of(new Role()));
        when(courtRepository.findById(model.getCourtId())).thenReturn(Optional.of(new Court()));

        assertThat(appAccessService.upsert(model)).isEqualTo(UpsertResult.UPDATED);

        verify(appAccessRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getUserId());
        verify(courtRepository, times(1)).findById(model.getCourtId());
        verify(roleRepository, times(1)).findById(model.getRoleId());
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Should fail to create/update when app access has been deleted")
    @Test
    void createAppAccessDeleted() {
        var model = new CreateAppAccessDTO();
        model.setId(UUID.randomUUID());
        model.setCourtId(UUID.randomUUID());
        model.setUserId(UUID.randomUUID());
        model.setRoleId(UUID.randomUUID());

        var entity = new AppAccess();
        entity.setDeletedAt(Timestamp.from(Instant.now()));

        when(appAccessRepository.findById(model.getId())).thenReturn(Optional.of(entity));

        assertThrows(
            ResourceInDeletedStateException.class,
            () -> appAccessService.upsert(model)
        );

        verify(appAccessRepository, times(1)).findById(model.getId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when user cannot be found")
    @Test
    void createAppAccessUserNotFound() {
        var model = new CreateAppAccessDTO();
        model.setId(UUID.randomUUID());
        model.setCourtId(UUID.randomUUID());
        model.setUserId(UUID.randomUUID());
        model.setRoleId(UUID.randomUUID());

        when(appAccessRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(model.getUserId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> appAccessService.upsert(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: User: " + model.getUserId());

        verify(appAccessRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getUserId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when court cannot be found")
    @Test
    void createAppAccessCourtNotFound() {
        var model = new CreateAppAccessDTO();
        model.setId(UUID.randomUUID());
        model.setCourtId(UUID.randomUUID());
        model.setUserId(UUID.randomUUID());
        model.setRoleId(UUID.randomUUID());

        when(appAccessRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(model.getUserId())).thenReturn(Optional.of(new User()));
        when(courtRepository.findById(model.getCourtId())).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> appAccessService.upsert(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Court: " + model.getCourtId());

        verify(appAccessRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getUserId());
        verify(courtRepository, times(1)).findById(model.getCourtId());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Should fail to create/update when role cannot be found")
    @Test
    void createAppAccessRoleNotFound() {
        var model = new CreateAppAccessDTO();
        model.setId(UUID.randomUUID());
        model.setCourtId(UUID.randomUUID());
        model.setUserId(UUID.randomUUID());
        model.setRoleId(UUID.randomUUID());

        when(appAccessRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(model.getUserId())).thenReturn(Optional.of(new User()));
        when(roleRepository.findById(model.getRoleId())).thenReturn(Optional.empty());
        when(courtRepository.findById(model.getCourtId())).thenReturn(Optional.of(new Court()));

        var message = assertThrows(
            NotFoundException.class,
            () -> appAccessService.upsert(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Role: " + model.getRoleId());

        verify(appAccessRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(model.getUserId());
        verify(courtRepository, times(1)).findById(model.getCourtId());
        verify(roleRepository, times(1)).findById(model.getRoleId());
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

        appAccessService.deleteById(id);

        assertThat(access.getDeletedAt()).isNotNull();
        assertFalse(access.isActive());

        verify(appAccessRepository, times(1)).findById(id);
        verify(appAccessRepository, times(1)).save(any());
    }

}
