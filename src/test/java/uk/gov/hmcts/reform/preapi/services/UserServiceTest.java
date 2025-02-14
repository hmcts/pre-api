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
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UserService.class)
public class UserServiceTest {
    private static User userEntity;
    private static User portalUserEntity;
    private static User appUserEntity;
    private static AppAccess appAccessEntity;
    private static AppAccess appAccessEntity2;
    private static PortalAccess portalAccessEntity;
    private static PortalAccess portalAccessEntity2;

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

    @MockBean
    private AppAccessService appAccessService;

    @MockBean
    private PortalAccessService portalAccessService;

    @MockBean
    private TermsAndConditionsRepository termsAndConditionsRepository;

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

        portalUserEntity = new User();
        portalUserEntity.setId(UUID.randomUUID());
        portalUserEntity.setFirstName("Portal");
        portalUserEntity.setLastName("Person");
        portalUserEntity.setEmail("portal@example.com");
        portalUserEntity.setOrganisation("Portal Org");

        appUserEntity = new User();
        appUserEntity.setId(UUID.randomUUID());
        appUserEntity.setFirstName("App");
        appUserEntity.setLastName("Person");
        appUserEntity.setEmail("app@example.com");
        appUserEntity.setOrganisation("App Org");

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
        userEntity.setAppAccess(Set.of(appAccessEntity));

        appAccessEntity2 = new AppAccess();
        appAccessEntity2.setId(UUID.randomUUID());
        appAccessEntity2.setUser(appUserEntity);
        appAccessEntity2.setCourt(court);
        appAccessEntity2.setRole(role);
        appAccessEntity2.setActive(true);
        appUserEntity.setAppAccess(Set.of(appAccessEntity2));

        portalAccessEntity = new PortalAccess();
        portalAccessEntity.setId(UUID.randomUUID());
        portalAccessEntity.setUser(userEntity);
        userEntity.setPortalAccess(Set.of(portalAccessEntity));

        portalAccessEntity2 = new PortalAccess();
        portalAccessEntity2.setId(UUID.randomUUID());
        portalAccessEntity2.setUser(portalUserEntity);
        portalUserEntity.setPortalAccess(Set.of(portalAccessEntity2));
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
            userRepository.findByIdAndDeletedAtIsNull(userEntity.getId())
        ).thenReturn(Optional.of(userEntity));

        var model = userService.findById(userEntity.getId());
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
    }

    @DisplayName("Find a user by it's id which doesn't exist")
    @Test
    void findUserByIdNotFound() {
        when(
            userRepository.findByIdAndDeletedAtIsNull(UUID.randomUUID())
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.findById(userEntity.getId())
        );

        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(userEntity.getId());
    }

    @DisplayName("Find all users and return a list of models")
    @Test
    void findAllUsersSuccess() {
        when(
            userRepository.searchAllBy(
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                null
            )
        ).thenReturn(new PageImpl<>(List.of(userEntity, portalUserEntity, appUserEntity)));

        var models = userService.findAllBy(null, null, null, null, null, null, false, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(3);

        assertAllUsers(models);
    }

    @DisplayName("Find all users when filtered by first name")
    @Test
    void findAllUsersFirstNameFilterSuccess() {
        when(
            userRepository.searchAllBy(
                userEntity.getFirstName(),
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                null
            )).thenReturn(new PageImpl<>(List.of(userEntity)));

        var models = userService.findAllBy(userEntity.getFirstName(), null, null, null, null, null, false, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(1);

        var model = models.get().toList().getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(model.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                       .getCourt()
                                                                                                       .getId());
    }

    @DisplayName("Find all users when filtered by email")
    @Test
    void findAllUsersEmailFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            userRepository.searchAllBy(null, userEntity.getEmail(), null, null, null, false, false, false, null,null)
        ).thenReturn(new PageImpl<>(List.of(userEntity)));

        var models = userService.findAllBy(null, userEntity.getEmail(), null, null, null, null, false, null, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(1);

        var model = models.get().toList().getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(model.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                       .getCourt()
                                                                                                       .getId());
    }

    @DisplayName("Find all users when filtered by organisation")
    @Test
    void findAllUsersOrganisationFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            userRepository.searchAllBy(
                null,
                null,
                userEntity.getOrganisation(),
                null,
                null,
                false,
                false,
                false,
                null,
                null
            )
        ).thenReturn(new PageImpl<>(List.of(userEntity)));

        var models = userService.findAllBy(
            null,
            null,
            userEntity.getOrganisation(),
            null,
            null,
            null,
            false,
            null,
            null
        );
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(1);

        var model = models.get().toList().getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(model.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                       .getCourt()
                                                                                                       .getId());
    }

    @DisplayName("Find all users when filtered by court")
    @Test
    void findAllUsersCourtFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            userRepository.searchAllBy(
                null,
                null,
                null,
                appAccessEntity.getCourt().getId(),
                null,
                false,
                false,
                false,
                null,
                null
            )
        ).thenReturn(new PageImpl<>(List.of(userEntity, appUserEntity)));

        var models = userService.findAllBy(
            null,
            null,
            null,
            appAccessEntity.getCourt().getId(),
            null,
            null,
            false,
            null,
            null
        );
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(2);

        var user1 = models.get().toList().getFirst();
        assertThat(user1.getId()).isEqualTo(userEntity.getId());
        assertThat(user1.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(user1.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(user1.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                       .getCourt()
                                                                                                       .getId());
        assertThat(user1.getPortalAccess().size()).isEqualTo(1);
        assertThat(user1.getPortalAccess().stream().toList().getFirst().getId()).isEqualTo(portalAccessEntity.getId());

        var user2 = models.get().toList().get(1);
        assertThat(user2.getId()).isEqualTo(appUserEntity.getId());
        assertThat(user2.getFirstName()).isEqualTo(appUserEntity.getFirstName());
        assertThat(user2.getPortalAccess().size()).isEqualTo(0);
        assertThat(user2.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity2
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(user2.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity2
                                                                                                       .getCourt()
                                                                                                       .getId());
    }

    @DisplayName("Find all users when filtered by role")
    @Test
    void findAllUsersRoleFilterSuccess() {
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);
        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(
            userRepository.searchAllBy(
                null,
                null,
                null,
                null,
                appAccessEntity.getRole().getId(),
                false,
                false,
                false,
                null,
                null
            )
        ).thenReturn(new PageImpl<>(List.of(userEntity, appUserEntity)));

        var models = userService.findAllBy(
            null,
            null,
            null,
            null,
            appAccessEntity.getRole().getId(),
            null,
            false,
            null,
            null
        );
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(2);

        var user1 = models.get().toList().getFirst();
        assertThat(user1.getId()).isEqualTo(userEntity.getId());
        assertThat(user1.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(user1.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(user1.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                       .getCourt()
                                                                                                       .getId());
        assertThat(user1.getPortalAccess().size()).isEqualTo(1);
        assertThat(user1.getPortalAccess().stream().toList().getFirst().getId()).isEqualTo(portalAccessEntity.getId());

        var user2 = models.get().toList().get(1);
        assertThat(user2.getId()).isEqualTo(appUserEntity.getId());
        assertThat(user2.getFirstName()).isEqualTo(appUserEntity.getFirstName());
        assertThat(user2.getPortalAccess().size()).isEqualTo(0);
        assertThat(user2.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity2
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(user2.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity2
                                                                                                       .getCourt()
                                                                                                       .getId());
    }

    @DisplayName("Find all users when filtered by court that doesn't exist")
    @Test
    void findAllUsersCourtFilterNotFound() {
        UUID courtId = UUID.randomUUID();
        when(courtRepository.existsById(courtId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> userService.findAllBy(null, null, null, courtId, null, null, false, null, null)
        );

        verify(courtRepository, times(1)).existsById(courtId);
        verify(roleRepository, never()).existsById(any());
        verify(userRepository, never()).searchAllBy(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(false),
            any(),
            any()
        );
    }

    @DisplayName("Find all users when filtered by role that doesn't exist")
    @Test
    void findAllUsersRoleFilterNotFound() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsById(roleId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> userService.findAllBy(null, null, null, null, roleId, null, false, null, null)
        );

        verify(courtRepository, never()).existsById(any());
        verify(roleRepository, times(1)).existsById(roleId);
        verify(userRepository, never()).searchAllBy(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(false),
            any(),
            any()
        );
    }

    @DisplayName("Delete a user by it's id")
    @Test
    void deleteUserByIdSuccess() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(true);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.of(portalAccessEntity));
        ArrayList<AppAccess> appAccessEntities = new ArrayList<>();
        appAccessEntities.add(appAccessEntity);
        when(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(appAccessEntities);
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.ofNullable(userEntity));

        userService.deleteById(userEntity.getId());

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userEntity.getId());
        verify(portalAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(portalAccessService, times(1)).deleteById(portalAccessEntity.getId());
        verify(appAccessRepository, times(1)).findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessService, times(1)).deleteById(appAccessEntity.getId());
        verify(userRepository, times(1)).saveAndFlush(userEntity);
    }

    @DisplayName("Delete a user by it's id when user is not attached to portal access or app access")
    @Test
    void deleteUserByIdNoAccessSuccess() {
        ArrayList<AppAccess> emptySet = new ArrayList<>(){};
        when(userRepository.existsByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(true);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.empty());
        when(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(emptySet);
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.ofNullable(userEntity));

        userService.deleteById(userEntity.getId());

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userEntity.getId());
        verify(portalAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(portalAccessRepository, never()).save(any());
        verify(appAccessRepository, times(1)).findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessRepository, never()).save(any());
        verify(userRepository, times(1)).saveAndFlush(userEntity);
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
        verify(portalAccessRepository, never()).save(any());
        verify(appAccessRepository, never()).findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessRepository, never()).save(any());
        verify(userRepository, never()).deleteById(userEntity.getId());
    }

    @DisplayName("Delete a user when App Access entry doesn't exist")
    @Test
    void deleteUserWithEmptyAppAccess() {
        when(userRepository.existsByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(true);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(Optional.of(portalAccessEntity));
        ArrayList<AppAccess> emptyAppAccessEntities = new ArrayList<>(){};
        when(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId()))
            .thenReturn(emptyAppAccessEntities);
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.ofNullable(userEntity));

        userService.deleteById(userEntity.getId());

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userEntity.getId());
        verify(portalAccessRepository, times(1))
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(portalAccessService, times(1)).deleteById(portalAccessEntity.getId());
        verify(appAccessRepository, times(1))
            .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userEntity.getId());
        verify(appAccessService, never()).deleteById(any());
        verify(userRepository, times(1)).saveAndFlush(userEntity);
    }

    @DisplayName("Create a user")
    @Test
    void createUserSuccess() {
        var model = new CreateUserDTO();
        model.setId(UUID.randomUUID());
        model.setFirstName("Example");
        model.setLastName("Example");
        model.setEmail("example@example.com");
        model.setAppAccess(Set.of());
        model.setPortalAccess(Set.of());

        when(userRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase(model.getEmail())).thenReturn(false);

        assertThat(userService.upsert(model)).isEqualTo(UpsertResult.CREATED);

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).saveAndFlush(any());
        verify(appAccessRepository, never()).deleteById(any());
        verify(appAccessService, never()).upsert(any());
        verify(portalAccessRepository, never()).existsById(any());
        verify(portalAccessService, never()).update(any());
    }

    @DisplayName("Update a user")
    @Test
    void updateUserSuccess() {
        var userId = UUID.randomUUID();

        var entity = new User();
        entity.setId(userId);
        entity.setFirstName("Example");
        entity.setLastName("Example");
        entity.setLastName("Example");
        entity.setEmail("example@example.com");

        var accessEntity = new AppAccess();
        accessEntity.setId(UUID.randomUUID());

        var portalEntity = new PortalAccess();
        portalEntity.setId(UUID.randomUUID());

        entity.setAppAccess(Set.of(accessEntity));
        entity.setPortalAccess(Set.of(portalEntity));

        var model = new CreateUserDTO();
        model.setId(userId);
        model.setFirstName("CHANGED");
        model.setLastName("Example");
        model.setEmail("example@example.com");
        model.setPortalAccess(Set.of());

        var accessModel = new CreateAppAccessDTO();
        accessModel.setId(UUID.randomUUID());
        model.setAppAccess(Set.of(accessModel));

        var portalModel = new CreatePortalAccessDTO();
        portalModel.setId(portalEntity.getId());
        model.setPortalAccess(Set.of(portalModel));

        when(userRepository.findById(model.getId())).thenReturn(Optional.of(entity));
        when(appAccessService.upsert(accessModel)).thenReturn(UpsertResult.CREATED);
        when(portalAccessRepository.existsById(portalModel.getId())).thenReturn(true);
        when(portalAccessService.update(portalModel)).thenReturn(UpsertResult.UPDATED);

        assertThat(userService.upsert(model)).isEqualTo(UpsertResult.UPDATED);

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).saveAndFlush(any());
        verify(appAccessService, times(1)).deleteById(accessEntity.getId());
        verify(appAccessService, times(1)).upsert(accessModel);
        verify(portalAccessRepository, times(1)).existsById(portalModel.getId());
        verify(portalAccessService, times(1)).update(portalModel);
    }

    @DisplayName("Update a user fails when portal access does not exist")
    @Test
    void updateUserPortalAccessNotFound() {
        var userId = UUID.randomUUID();

        var entity = new User();
        entity.setId(userId);
        entity.setFirstName("Example");
        entity.setLastName("Example");
        entity.setLastName("Example");
        entity.setEmail("example@example.com");

        var accessEntity = new AppAccess();
        accessEntity.setId(UUID.randomUUID());

        var portalEntity = new PortalAccess();
        portalEntity.setId(UUID.randomUUID());

        entity.setAppAccess(Set.of(accessEntity));
        entity.setPortalAccess(Set.of(portalEntity));

        var model = new CreateUserDTO();
        model.setId(userId);
        model.setFirstName("CHANGED");
        model.setLastName("Example");
        model.setEmail("example@example.com");
        model.setPortalAccess(Set.of());

        var accessModel = new CreateAppAccessDTO();
        accessModel.setId(UUID.randomUUID());
        model.setAppAccess(Set.of(accessModel));

        var portalModel = new CreatePortalAccessDTO();
        portalModel.setId(UUID.randomUUID());
        model.setPortalAccess(Set.of(portalModel));

        when(userRepository.findById(model.getId())).thenReturn(Optional.of(entity));
        when(appAccessService.upsert(accessModel)).thenReturn(UpsertResult.CREATED);
        when(portalAccessRepository.existsById(portalModel.getId())).thenReturn(false);
        when(portalAccessService.update(portalModel)).thenReturn(UpsertResult.UPDATED);

        var message = assertThrows(
            NotFoundException.class,
            () -> userService.upsert(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Portal Access: " + portalModel.getId());

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, never()).saveAndFlush(any());
        verify(appAccessRepository, never()).deleteById(accessEntity.getId());
        verify(appAccessService, never()).upsert(accessModel);
        verify(portalAccessRepository, times(1)).existsById(portalModel.getId());
        verify(portalAccessService, never()).update(portalModel);
    }

    @DisplayName("Should throw resource deleted error when updating a user that has been deleted")
    @Test
    void updateUserDeleted() {
        var userId = UUID.randomUUID();

        var entity = new User();
        entity.setId(userId);
        entity.setFirstName("Example");
        entity.setLastName("Example");
        entity.setLastName("Example");
        entity.setEmail("example@example.com");
        entity.setAppAccess(Set.of());
        entity.setDeletedAt(Timestamp.from(Instant.now()));

        var model = new CreateUserDTO();
        model.setId(userId);
        model.setFirstName("CHANGED");
        model.setLastName("Example");
        model.setEmail("example@example.com");

        var accessModel = new CreateAppAccessDTO();
        accessModel.setId(UUID.randomUUID());
        model.setAppAccess(Set.of(accessModel));

        when(userRepository.findById(model.getId())).thenReturn(Optional.of(entity));

        var message = assertThrows(
            ResourceInDeletedStateException.class,
            () -> userService.upsert(model)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Resource UserDTO(" + model.getId() + ") is in a deleted state and cannot be updated");

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Should fail to create user when a user with the same email address already exists")
    @Test
    void updateUserEmailAlreadyExists() {
        var userId = UUID.randomUUID();
        var model = new CreateUserDTO();
        model.setId(userId);
        model.setEmail("example@example.com");

        when(userRepository.findById(model.getId())).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase(model.getEmail())).thenReturn(true);

        var message = assertThrows(
            ConflictException.class,
            () -> userService.upsert(model)
        ).getMessage();

        assertThat(message).isEqualTo("Conflict: User with email: " + model.getEmail() + " already exists");

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Get a user's access information by email")
    @Test
    void findUserAccessByEmailSuccess() {
        when(userRepository
                 .findByEmailIgnoreCaseAndDeletedAtIsNull(appUserEntity.getEmail())
        ).thenReturn(Optional.of(appUserEntity));

        var userAccess = userService.findByEmail(appUserEntity.getEmail());

        assertThat(userAccess).isNotNull();
        assertThat(userAccess.getAppAccess()).isNotNull();
        assertThat(userAccess.getAppAccess().stream().findFirst()).isNotNull();
        assertThat(userAccess.getAppAccess().stream().findFirst().get().getId()).isEqualTo(appAccessEntity2.getId());
        assertThat(userAccess.getAppAccess().stream().findFirst().get().getCourt().getId())
            .isEqualTo(appAccessEntity2.getCourt().getId());
        assertThat(userAccess.getAppAccess().stream().findFirst().get().getRole().getId())
            .isEqualTo(appAccessEntity2.getRole().getId());
        assertThat(userAccess.getUser().getId()).isEqualTo(appUserEntity.getId());
    }

    @DisplayName("Get a user's access information by email when no app access found")
    @Test
    void findUserAccessByEmailNotFound() {
        when(userRepository
                 .findByEmailIgnoreCaseAndDeletedAtIsNull(userEntity.getEmail())
        ).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> userService.findByEmail(userEntity.getEmail())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: User: " + userEntity.getEmail());

    }

    private void assertAllUsers(Page<UserDTO> models) {
        var user1 = models.get().toList().getFirst();
        assertThat(user1.getId()).isEqualTo(userEntity.getId());
        assertThat(user1.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(user1.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(user1.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                       .getCourt()
                                                                                                       .getId());
        assertThat(user1.getPortalAccess().size()).isEqualTo(1);
        assertThat(user1.getPortalAccess().stream().toList().getFirst().getId()).isEqualTo(portalAccessEntity.getId());
        var user2 = models.get().toList().get(1);
        assertThat(user2.getId()).isEqualTo(portalUserEntity.getId());
        assertThat(user2.getFirstName()).isEqualTo(portalUserEntity.getFirstName());
        assertThat(user2.getAppAccess().size()).isEqualTo(0);
        assertThat(user2.getPortalAccess().stream().toList().getFirst().getId()).isEqualTo(portalAccessEntity2.getId());
        var user3 = models.get().toList().get(2);
        assertThat(user3.getId()).isEqualTo(appUserEntity.getId());
        assertThat(user3.getFirstName()).isEqualTo(appUserEntity.getFirstName());
        assertThat(user3.getPortalAccess().size()).isEqualTo(0);
        assertThat(user3.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity2
                                                                                                      .getRole()
                                                                                                      .getId());
        assertThat(user3.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity2
                                                                                                       .getCourt()
                                                                                                       .getId());
    }

    @DisplayName("Should undelete a user successfully when user is marked as deleted")
    @Test
    void undeleteSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setDeletedAt(Timestamp.from(Instant.now()));

        var appAccess = new AppAccess();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUser(user);
        appAccess.setDeletedAt(Timestamp.from(Instant.now()));
        appAccess.setActive(false);

        var portalAccess = new PortalAccess();
        portalAccess.setId(UUID.randomUUID());
        portalAccess.setUser(user);
        portalAccess.setDeletedAt(Timestamp.from(Instant.now()));

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(appAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(user.getId())).thenReturn(List.of(appAccess));
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(user.getId()))
            .thenReturn(List.of(portalAccess));

        userService.undelete(user.getId());

        verify(userRepository, times(1)).findById(user.getId());
        verify(userRepository, times(1)).save(user);
        verify(appAccessRepository, times(1)).findAllByUser_IdAndDeletedAtIsNotNull(user.getId());
        verify(appAccessRepository, times(1)).save(appAccess);
        verify(portalAccessRepository, times(1)).findAllByUser_IdAndDeletedAtIsNotNull(user.getId());
        verify(portalAccessRepository, times(1)).save(portalAccess);
    }

    @DisplayName("Should do nothing when user is not deleted")
    @Test
    void undeleteNotDeletedSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.undelete(user.getId());

        verify(userRepository, times(1)).findById(user.getId());
        verify(userRepository, never()).save(user);
        verify(appAccessRepository, never()).save(any());
        verify(portalAccessRepository, never()).save(any());
    }

    @DisplayName("Should throw not found exception when user cannot be found")
    @Test
    void undeleteNotFound() {
        var userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> userService.undelete(userId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: User: " + userId);

        verify(userRepository, times(1)).findById(userId);
        verify(userRepository, never()).save(any());
        verify(appAccessRepository, never()).save(any());
        verify(portalAccessRepository, never()).save(any());
    }

    @DisplayName("Should create a new user and portal access entity on create invite")
    @Test
    void createNewUserFromInvite() {
        var dto = new CreateInviteDTO();
        dto.setUserId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Example");
        dto.setEmail("example@example.com");

        when(userRepository.findById(dto.getUserId())).thenReturn(Optional.empty());
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(dto.getUserId()))
            .thenReturn(Optional.empty());

        assertThat(userService.upsert(dto)).isEqualTo(UpsertResult.CREATED);

        verify(userRepository, times(1)).findById(dto.getUserId());
        verify(portalAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(dto.getUserId());
        verify(portalAccessRepository, times(1)).save(any());
    }

    @DisplayName("Should fail create a new user and portal access entity on create invite when user has been deleted")
    @Test
    void createNewUserFromInviteUserResourceDeleted() {
        var dto = new CreateInviteDTO();
        dto.setUserId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Example");
        dto.setEmail("example@example.com");

        var user = new User();
        user.setDeletedAt(Timestamp.from(Instant.now()));

        when(userRepository.findById(dto.getUserId())).thenReturn(Optional.of(user));
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(dto.getUserId()))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceInDeletedStateException.class,
            () -> userService.upsert(dto)
        );

        verify(userRepository, times(1)).findById(dto.getUserId());
        verify(portalAccessRepository, never()).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(dto.getUserId());
        verify(portalAccessRepository, never()).save(any());
    }

    @DisplayName("Should return updated when invitation has already been sent")
    @Test
    void createNewUserFromInviteUpdated() {
        var dto = new CreateInviteDTO();
        dto.setUserId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Example");
        dto.setEmail("example@example.com");

        var user = new User();
        var portalAccess = new PortalAccess();

        when(userRepository.findById(dto.getUserId())).thenReturn(Optional.of(user));
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(dto.getUserId()))
            .thenReturn(Optional.of(portalAccess));

        assertThat(userService.upsert(dto)).isEqualTo(UpsertResult.UPDATED);

        verify(userRepository, times(1)).findById(dto.getUserId());
        verify(portalAccessRepository, times(1)).findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(dto.getUserId());
        verify(portalAccessRepository, never()).save(any());
    }
}
