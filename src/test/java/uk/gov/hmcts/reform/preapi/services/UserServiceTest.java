package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UserService.class)
public class UserServiceTest {
    private static User userEntity;
    private static AppAccess appAccessEntity;
    private static PortalAccess portalAccessEntity;

    @MockBean
    private AppAccessRepository appAccessRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private CourtRepository courtRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PortalAccessRepository portalAccessRepository;

    @Autowired
    private UserService userService;

    @BeforeAll
    static void setUp() {
        userEntity = new User();
        userEntity.setId(UUID.randomUUID());
        userEntity.setFirstName("Example");
        userEntity.setLastName("Person");
        userEntity.setEmail("example@example.com");
        userEntity.setOrganisation("Example Org");

        var court = new Court();
        court.setId(UUID.randomUUID());
        var role = new Role();
        role.setId(UUID.randomUUID());

        appAccessEntity = new AppAccess();
        appAccessEntity.setId(UUID.randomUUID());
        appAccessEntity.setUser(userEntity);
        appAccessEntity.setCourt(court);
        appAccessEntity.setRole(role);
        appAccessEntity.setActive(true);

        portalAccessEntity = new PortalAccess();
        portalAccessEntity.setId(UUID.randomUUID());
        portalAccessEntity.setUser(userEntity);
    }

    @BeforeEach
    void reset() {
        userEntity.setDeletedAt(null);
        appAccessEntity.setDeletedAt(null);
        portalAccessEntity.setDeletedAt(null);
    }

    @DisplayName("Find a user by it's id and return a model")
    @Test
    void findUserByIdSuccess() {
        when(
            appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId())
        ).thenReturn(Optional.of(appAccessEntity));

        var model = userService.findById(userEntity.getId());
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find a user by it's id which doesn't exist")
    @Test
    void findUserByIdNotFound() {
        when(
            appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(UUID.randomUUID())
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.findById(userEntity.getId())
        );

        verify(appAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
    }

    @DisplayName("Find all users and return a list of models")
    @Test
    void findAllUsersSuccess() {
        when(
            appAccessRepository.searchAllBy(null, null, null, null, null, null)
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(null, null, null, null, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by first name")
    @Test
    void findAllUsersFirstNameFilterSuccess() {
        when(
            appAccessRepository.searchAllBy(userEntity.getFirstName(), null, null, null, null, null)
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(userEntity.getFirstName(), null, null, null, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by last name")
    @Test
    void findAllUsersLastNameFilterSuccess() {
        when(
            appAccessRepository.searchAllBy(null, userEntity.getLastName(), null, null, null, null)
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(null, userEntity.getLastName(), null, null, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by email")
    @Test
    void findAllUsersEmailFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            appAccessRepository.searchAllBy(null, null, userEntity.getEmail(), null, null, null)
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(null, null, userEntity.getEmail(), null, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by organisation")
    @Test
    void findAllUsersOrganisationFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            appAccessRepository.searchAllBy(null, null, null, userEntity.getOrganisation(), null, null)
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(null, null, null, userEntity.getOrganisation(), null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by court")
    @Test
    void findAllUsersCourtFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            appAccessRepository.searchAllBy(null, null, null, null, appAccessEntity.getCourt().getId(), null)
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(null, null, null, null, appAccessEntity.getCourt().getId(), null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by role")
    @Test
    void findAllUsersRoleFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            appAccessRepository.searchAllBy(null, null, null, null, null, appAccessEntity.getRole().getId())
        ).thenReturn(List.of(appAccessEntity));

        var models = userService.findAllBy(null, null, null, null, null, appAccessEntity.getRole().getId());
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.size()).isEqualTo(1);

        var model = models.getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getRole().getId()).isEqualTo(appAccessEntity.getRole().getId());
        assertThat(model.getCourt().getId()).isEqualTo(appAccessEntity.getCourt().getId());
    }

    @DisplayName("Find all users when filtered by court that doesn't exist")
    @Test
    void findAllUsersCourtFilterNotFound() {
        UUID courtId = UUID.randomUUID();
        when(courtRepository.existsById(courtId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> userService.findAllBy(null, null, null, null, courtId, null)
        );

        verify(courtRepository, times(1)).existsById(courtId);
        verify(roleRepository, never()).existsById(any());
        verify(appAccessRepository, never()).searchAllBy(any(), any(), any(), any(), any(), any());
    }

    @DisplayName("Find all users when filtered by role that doesn't exist")
    @Test
    void findAllUsersRoleFilterNotFound() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsById(roleId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> userService.findAllBy(null, null, null, null, null, roleId)
        );

        verify(courtRepository, never()).existsById(any());
        verify(roleRepository, times(1)).existsById(roleId);
        verify(appAccessRepository, never()).searchAllBy(any(), any(), any(), any(), any(), any());
    }

    @DisplayName("Delete a user by it's id")
    @Test
    void deleteUserByIdSuccess() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(true);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.of(portalAccessEntity));
        when(appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.of(appAccessEntity));

        userService.deleteById(userEntity.getId());

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userEntity.getId());
        verify(portalAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(portalAccessRepository, times(1)).deleteById(portalAccessEntity.getId());
        verify(appAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessRepository, times(1)).deleteById(appAccessEntity.getId());
        verify(userRepository, times(1)).deleteById(userEntity.getId());
    }

    @DisplayName("Delete a user by it's id when user is not attached to portal access or app access")
    @Test
    void deleteUserByIdNoAccessSuccess() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(true);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.empty());
        when(appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.empty());

        userService.deleteById(userEntity.getId());

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userEntity.getId());
        verify(portalAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(portalAccessRepository, never()).deleteById(portalAccessEntity.getId());
        verify(appAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessRepository, never()).deleteById(appAccessEntity.getId());
        verify(userRepository, times(1)).deleteById(userEntity.getId());
    }

    @DisplayName("Delete a user when id doesn't exist")
    @Test
    void deleteUserByIdNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> userService.deleteById(userId)
        );

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userId);
        verify(portalAccessRepository, never()).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(portalAccessRepository, never()).deleteById(portalAccessEntity.getId());
        verify(appAccessRepository, never()).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessRepository, never()).deleteById(appAccessEntity.getId());
        verify(userRepository, never()).deleteById(userEntity.getId());
    }

    @DisplayName("Create a user")
    @Test
    void createUserSuccess() {
        var userId = UUID.randomUUID();
        var courtId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var userModel = new CreateUserDTO();
        userModel.setId(userId);
        userModel.setCourtId(courtId);
        userModel.setRoleId(roleId);
        var court = new Court();
        court.setId(courtId);
        var role = new Role();
        role.setId(roleId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(courtRepository.findById(courtId)).thenReturn(Optional.of(court));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.empty());

        var result = userService.upsert(userModel);

        assertThat(result).isEqualTo(UpsertResult.CREATED);

        verify(userRepository, times(1)).findById(userId);
        verify(courtRepository, times(1)).findById(courtId);
        verify(roleRepository, times(1)).findById(roleId);
        verify(appAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId);
        verify(userRepository, times(1)).save(any());
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Update a user")
    @Test
    void updateUserSuccess() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.of(userEntity));
        when(courtRepository.findById(userModel.getCourtId())).thenReturn(Optional.of(appAccessEntity.getCourt()));
        when(roleRepository.findById(userModel.getRoleId())).thenReturn(Optional.of(appAccessEntity.getRole()));
        when(appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userModel.getId()))
            .thenReturn(Optional.of(appAccessEntity));

        var result = userService.upsert(userModel);

        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, times(1)).findById(userModel.getCourtId());
        verify(roleRepository, times(1)).findById(userModel.getRoleId());
        verify(appAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userModel.getId());
        verify(userRepository, times(1)).save(any());
        verify(appAccessRepository, times(1)).save(any());
    }

    @DisplayName("Create/update a user is deleted")
    @Test
    void updateUserDeletedBadRequest() {
        userEntity.setDeletedAt(Timestamp.from(Instant.now()));

        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.of(userEntity));

        assertThrows(
            ResourceInDeletedStateException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());

    }

    @DisplayName("Create a user and court doesn't exist")
    @Test
    void createUserCourtIdNotFound() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.empty());
        when(courtRepository.findById(userModel.getCourtId())).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, times(1)).findById(userModel.getCourtId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Create a user and court not set")
    @Test
    void createUserCourtIdNullNotFound() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(null);
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, never()).findById(userModel.getCourtId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Create a user and role doesn't exist")
    @Test
    void createUserRoleIdNotFound() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.empty());
        when(courtRepository.findById(userModel.getCourtId())).thenReturn(Optional.of(appAccessEntity.getCourt()));
        when(roleRepository.findById(userModel.getRoleId())).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, times(1)).findById(userModel.getCourtId());
        verify(roleRepository, times(1)).findById(userModel.getRoleId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Create a user and role not set")
    @Test
    void createUserRoleIdNullNotFound() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(null);
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.empty());
        when(courtRepository.findById(userModel.getCourtId())).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, times(1)).findById(userModel.getCourtId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Update a user and court doesn't exist")
    @Test
    void updateUserCourtIdNotFound() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.of(userEntity));
        when(courtRepository.findById(userModel.getCourtId())).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, times(1)).findById(userModel.getCourtId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
    }

    @DisplayName("Update a user and role doesn't exist")
    @Test
    void updateUserRoleIdNotFound() {
        var userModel = new CreateUserDTO();
        userModel.setId(userEntity.getId());
        userModel.setCourtId(appAccessEntity.getCourt().getId());
        userModel.setRoleId(appAccessEntity.getRole().getId());
        userModel.setFirstName("Test Name");

        when(userRepository.findById(userModel.getId())).thenReturn(Optional.of(userEntity));
        when(courtRepository.findById(userModel.getCourtId())).thenReturn(Optional.of(appAccessEntity.getCourt()));
        when(roleRepository.findById(userModel.getRoleId())).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.upsert(userModel)
        );

        verify(userRepository, times(1)).findById(userModel.getId());
        verify(courtRepository, times(1)).findById(userModel.getCourtId());
        verify(roleRepository, times(1)).findById(userModel.getRoleId());
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
    }
}
