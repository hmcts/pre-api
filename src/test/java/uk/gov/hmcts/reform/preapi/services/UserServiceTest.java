package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchUsers;
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
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AppAccessService appAccessService;

    @MockitoBean
    private PortalAccessService portalAccessService;

    @MockitoBean
    private TermsAndConditionsService termsAndConditionsService;

    private static SearchUsers searchUsers;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        userEntity = HelperFactory.createUser("Example", "Person", "example@example.com",
                                              null, null, "Example Org");

        portalUserEntity = HelperFactory.createUser("Portal", "Person", "portal@example.com",
                                                    null, null, "Portal Org");

        appUserEntity = HelperFactory.createUser("App", "Person", "app@example.com",
                                                    null, null, "App Org");

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

        searchUsers = mock(SearchUsers.class);
        when(searchUsers.getRoleId()).thenReturn(appAccessEntity.getRole().getId());

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

        searchUsers = mock(SearchUsers.class);
        when(searchUsers.getName()).thenReturn(null);
        when(searchUsers.getFirstName()).thenReturn(null);
        when(searchUsers.getLastName()).thenReturn(null);
        when(searchUsers.getEmail()).thenReturn(null);
        when(searchUsers.getOrganisation()).thenReturn(null);
        when(searchUsers.getCourtId()).thenReturn(null);
        when(searchUsers.getRoleId()).thenReturn(null);
        when(searchUsers.getAccessType()).thenReturn(null);
        when(searchUsers.getAppActive()).thenReturn(null);

        // Defaults for happy path
        when(courtRepository.existsById(appAccessEntity.getCourt().getId())).thenReturn(true);

        when(roleRepository.existsById(appAccessEntity.getRole().getId())).thenReturn(true);
        when(roleRepository.findById(appAccessEntity.getRole().getId())).thenReturn(Optional.of(role));

        when(userRepository.findByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(userRepository.existsByIdAndDeletedAtIsNull(userEntity.getId())).thenReturn(true);
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.ofNullable(userEntity));

        when(userRepository.searchAllBy(
                null,
                null,
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
            )).thenReturn(new PageImpl<>(List.of(userEntity, portalUserEntity, appUserEntity)));
    }

    @AfterEach
    void reset() {
        userEntity.setDeletedAt(null);
        appAccessEntity.setDeletedAt(null);
        portalAccessEntity.setDeletedAt(null);
    }

    @DisplayName("Find a user by it's id and return a model")
    @Test
    void findUserByIdSuccess() {
        var model = userService.findById(userEntity.getId());
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(userEntity.getFirstName());
    }

    @DisplayName("Find a user by it's id which doesn't exist")
    @Test
    void findUserByIdNotFound() {
        UUID randomUserId = UUID.randomUUID();
        when(
            userRepository.findByIdAndDeletedAtIsNull(randomUserId)
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.findById(randomUserId)
        );

        verify(userRepository, times(1))
            .findByIdAndDeletedAtIsNull(randomUserId);
    }

    @DisplayName("Find all users and return a list of models")
    @Test
    void findAllUsersSuccess() {
        var models = userService.findAllBy(searchUsers, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(3);

        assertAllUsers(models);
    }

    @DisplayName("Find all users when filtered by first name")
    @Test
    void findAllUsersFirstNameFilterSuccess() {
        // using name param
        when(searchUsers.getName()).thenReturn(userEntity.getFirstName());

        when(
            userRepository.searchAllBy(
                userEntity.getFirstName(),
                null,
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
            )).thenReturn(new PageImpl<>(List.of(userEntity)));

        var models = userService.findAllBy(
            searchUsers,
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
        // using first name param
        when(searchUsers.getName()).thenReturn(null);
        when(searchUsers.getFirstName()).thenReturn(userEntity.getFirstName());
        when(
            userRepository.searchAllBy(
                null,
                userEntity.getFirstName(),
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
            )).thenReturn(new PageImpl<>(List.of(userEntity)));

        var models2 = userService.findAllBy(searchUsers, null);
        assertThat(models2.isEmpty()).isFalse();
        assertThat(models2.getTotalElements()).isEqualTo(1);

        var model2 = models2.get().toList().getFirst();
        assertThat(model2.getId()).isEqualTo(userEntity.getId());
        assertThat(model2.getFirstName()).isEqualTo(userEntity.getFirstName());
        assertThat(model2.getAppAccess().stream().toList().getFirst().getRole().getId()).isEqualTo(appAccessEntity
                                                                                                       .getRole()
                                                                                                       .getId());
        assertThat(model2.getAppAccess().stream().toList().getFirst().getCourt().getId()).isEqualTo(appAccessEntity
                                                                                                        .getCourt()
                                                                                                        .getId());
    }

    @Test
    @DisplayName("Find all users when filtered by last name")
    void findAllUsersLastNameFilterSuccess() {
        when(searchUsers.getLastName()).thenReturn(userEntity.getLastName());
        when(
            userRepository.searchAllBy(
                null,
                null,
                userEntity.getLastName(),
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

        var models = userService
            .findAllBy(searchUsers, null);
        assertThat(models.isEmpty()).isFalse();
        assertThat(models.getTotalElements()).isEqualTo(1);

        var model = models.get().toList().getFirst();
        assertThat(model.getId()).isEqualTo(userEntity.getId());
        assertThat(model.getLastName()).isEqualTo(userEntity.getLastName());
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
        when(searchUsers.getEmail()).thenReturn(userEntity.getEmail());
        when(
            userRepository.searchAllBy(
                null,
                null,
                null,
                userEntity.getEmail(),
                null,
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
            searchUsers,
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

    @DisplayName("Find all users when filtered by organisation")
    @Test
    void findAllUsersOrganisationFilterSuccess() {
        when(searchUsers.getOrganisation()).thenReturn(userEntity.getOrganisation());
        when(
            userRepository.searchAllBy(
                null,
                null,
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
            searchUsers,
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
        when(searchUsers.getCourtId()).thenReturn(appAccessEntity.getCourt().getId());
        when(
            userRepository.searchAllBy(
                null,
                null,
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
            searchUsers,
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
        var role = new Role();
        role.setName("Some Role");
        when(searchUsers.getRoleId()).thenReturn(role.getId());
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(
            userRepository.searchAllBy(
                null,
                null,
                null,
                null,
                null,
                null,
                role.getId(),
                false,
                false,
                false,
                null,
                null
            )
        ).thenReturn(new PageImpl<>(List.of(userEntity, appUserEntity)));

        var models = userService.findAllBy(searchUsers, null);
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
        UUID randomCourtId = UUID.randomUUID();
        when(searchUsers.getCourtId()).thenReturn(randomCourtId);
        when(courtRepository.existsById(randomCourtId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> userService.findAllBy(searchUsers, null)
        );

        verify(courtRepository, times(1)).existsById(randomCourtId);
        verify(roleRepository, never()).existsById(any());
        verify(userRepository, never()).searchAllBy(
            any(),
            any(),
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
        UUID roleId = UUID.randomUUID();
        when(searchUsers.getRoleId()).thenReturn(roleId);
        when(roleRepository.existsById(roleId)).thenReturn(false);

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        assertThrows(
            NotFoundException.class,
            () -> userService.findAllBy(searchUsers, null)
        );

        verify(courtRepository, never()).existsById(any());
        verify(roleRepository, times(1)).findById(roleId);
        verify(userRepository, never()).searchAllBy(
            any(),
            any(),
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

    @DisplayName("Delete a user by its id")
    @Test
    void deleteUserByIdSuccess() {
        userService.deleteById(userEntity.getId());

        verify(userRepository, times(1)).existsByIdAndDeletedAtIsNull(userEntity.getId());
        verify(portalAccessService, times(1)).deleteByUserId(userEntity.getId());
        verify(appAccessService, times(1)).deleteByUserId(userEntity.getId());
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
        verifyNoInteractions(portalAccessService);
        verifyNoInteractions(appAccessService);
        verify(userRepository, never()).deleteById(userEntity.getId());
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
        verifyNoInteractions(appAccessService);
        verifyNoInteractions(portalAccessService);
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
        when(portalAccessService.exists(portalModel.getId())).thenReturn(true);
        when(portalAccessService.update(portalModel)).thenReturn(UpsertResult.UPDATED);

        assertThat(userService.upsert(model)).isEqualTo(UpsertResult.UPDATED);

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, times(1)).saveAndFlush(any());
        verify(appAccessService, times(1)).deleteById(accessEntity.getId());
        verify(appAccessService, times(1)).upsert(accessModel);
        verify(portalAccessService, times(1)).exists(portalModel.getId());
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
        when(portalAccessService.exists(portalModel.getId())).thenReturn(false);
        when(portalAccessService.update(portalModel)).thenReturn(UpsertResult.UPDATED);

        var message = assertThrows(
            NotFoundException.class,
            () -> userService.upsert(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Portal Access: " + portalModel.getId());

        verify(userRepository, times(1)).findById(model.getId());
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(appAccessService);
        verify(portalAccessService, times(1)).exists(portalModel.getId());
        verifyNoMoreInteractions(portalAccessService);
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
                 .findByEmailOrAlternativeEmailIgnoreCaseAndDeletedAtIsNull(appUserEntity.getEmail())
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

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.undelete(user.getId());

        verify(userRepository, times(1)).findById(user.getId());
        verify(userRepository, times(1)).save(user);
        verify(appAccessService, times(1)).undeleteByUserId(user.getId());
        verify(portalAccessService, times(1)).undeleteByUserId(user.getId());
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
        verifyNoInteractions(appAccessService, portalAccessService);
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
        verifyNoInteractions(appAccessService, portalAccessService);
    }

    @DisplayName("Should create a new user and portal access entity on create invite")
    @Test
    void createNewUserFromInvite() {
        var invitedUser = new CreateInviteDTO();
        invitedUser.setUserId(UUID.randomUUID());
        invitedUser.setFirstName("Example");
        invitedUser.setLastName("Example");
        invitedUser.setEmail("example@example.com");

        when(userRepository.findById(invitedUser.getUserId())).thenReturn(Optional.empty());

        assertThat(userService.upsert(invitedUser)).isEqualTo(UpsertResult.CREATED);

        verify(userRepository, times(1)).findById(invitedUser.getUserId());

        ArgumentCaptor<UUID> uuidArgumentCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<AccessStatus> accessStatusArgumentCaptor = ArgumentCaptor.forClass(AccessStatus.class);

        verify(portalAccessService, times(1))
            .upsertPortalAccessEntity(uuidArgumentCaptor.capture(),
                                      userArgumentCaptor.capture(),
                                      accessStatusArgumentCaptor.capture(),
                                      any());
        assertThat(uuidArgumentCaptor.getValue()).isEqualTo(invitedUser.getUserId());
        assertThat(userArgumentCaptor.getValue().getId()).isEqualTo(invitedUser.getUserId());
        assertThat(accessStatusArgumentCaptor.getValue()).isEqualTo(AccessStatus.INVITATION_SENT);

        verifyNoMoreInteractions(portalAccessService);
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

        assertThrows(
            ResourceInDeletedStateException.class,
            () -> userService.upsert(dto)
        );

        verify(userRepository, times(1)).findById(dto.getUserId());
        verifyNoInteractions(portalAccessService);
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

        when(userRepository.findById(dto.getUserId())).thenReturn(Optional.of(user));
        when(portalAccessService.isNotDeletedPortalUser(dto.getUserId())).thenReturn(Boolean.TRUE);

        assertThat(userService.upsert(dto)).isEqualTo(UpsertResult.UPDATED);

        verify(userRepository, times(1)).findById(dto.getUserId());
        verify(portalAccessService, times(1)).isNotDeletedPortalUser(dto.getUserId());
        verifyNoMoreInteractions(portalAccessService);
    }

    @DisplayName("Should find user by original email successfully")
    @Test
    void findByOriginalEmailSuccess() {
        String email = "test@example.com";
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email))
            .thenReturn(Optional.of(userEntity));

        Optional<User> result = userService.findByOriginalEmail(email);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(userEntity.getId());
        assertThat(result.get().getEmail()).isEqualTo(userEntity.getEmail());
        verify(userRepository, times(1)).findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    @DisplayName("Should return empty when user with original email not found")
    @Test
    void findByOriginalEmailNotFound() {
        String email = "notfound@example.com";
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email))
            .thenReturn(Optional.empty());

        Optional<User> result = userService.findByOriginalEmail(email);

        assertThat(result).isEmpty();
        verify(userRepository, times(1)).findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    @DisplayName("Should find user by alternative email successfully")
    @Test
    void findByAlternativeEmailSuccess() {
        String alternativeEmail = "alt@example.com";
        User userWithAltEmail = new User();
        userWithAltEmail.setId(UUID.randomUUID());
        userWithAltEmail.setEmail("original@example.com");
        userWithAltEmail.setAlternativeEmail(alternativeEmail);

        when(userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail))
            .thenReturn(Optional.of(userWithAltEmail));

        Optional<User> result = userService.findByAlternativeEmail(alternativeEmail);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(userWithAltEmail.getId());
        assertThat(result.get().getAlternativeEmail()).isEqualTo(alternativeEmail);
        verify(userRepository, times(1)).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail);
    }

    @DisplayName("Should return empty when user with alternative email not found")
    @Test
    void findByAlternativeEmailNotFound() {
        String alternativeEmail = "notfound@example.com";
        when(userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail))
            .thenReturn(Optional.empty());

        Optional<User> result = userService.findByAlternativeEmail(alternativeEmail);

        assertThat(result).isEmpty();
        verify(userRepository, times(1)).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail);
    }

    @DisplayName("Should find user by original email with portal access successfully")
    @Test
    void findByOriginalEmailWithPortalAccessSuccess() {
        String email = "test@example.com";
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNullWithPortalAccess(email))
            .thenReturn(Optional.of(userEntity));

        Optional<User> result = userService.findByOriginalEmailWithPortalAccess(email);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(userEntity.getId());
        assertThat(result.get().getEmail()).isEqualTo(userEntity.getEmail());
        verify(userRepository, times(1)).findByEmailIgnoreCaseAndDeletedAtIsNullWithPortalAccess(email);
    }

    @DisplayName("Should return empty when user with original email not found with portal access")
    @Test
    void findByOriginalEmailWithPortalAccessNotFound() {
        String email = "notfound@example.com";
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNullWithPortalAccess(email))
            .thenReturn(Optional.empty());

        Optional<User> result = userService.findByOriginalEmailWithPortalAccess(email);

        assertThat(result).isEmpty();
        verify(userRepository, times(1)).findByEmailIgnoreCaseAndDeletedAtIsNullWithPortalAccess(email);
    }

    @DisplayName("Should update alternative email successfully")
    @Test
    void updateAlternativeEmailSuccess() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        String alternativeEmail = "newalt@example.com";

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail))
            .thenReturn(Optional.empty());

        userService.updateAlternativeEmail(userId, alternativeEmail);

        assertThat(user.getAlternativeEmail()).isEqualTo(alternativeEmail);
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, times(1)).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail);
        verify(userRepository, times(1)).saveAndFlush(user);
    }

    @DisplayName("Should update alternative email to null when null is provided")
    @Test
    void updateAlternativeEmailToNull() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        user.setAlternativeEmail("oldalt@example.com");

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));

        userService.updateAlternativeEmail(userId, null);

        assertThat(user.getAlternativeEmail()).isNull();
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, never()).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository, times(1)).saveAndFlush(user);
    }

    @DisplayName("Should update alternative email to null when empty string is provided")
    @Test
    void updateAlternativeEmailToEmptyString() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        user.setAlternativeEmail("oldalt@example.com");

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));

        userService.updateAlternativeEmail(userId, "   ");

        assertThat(user.getAlternativeEmail()).isNull();
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, never()).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository, times(1)).saveAndFlush(user);
    }

    @DisplayName("Should trim alternative email when updating")
    @Test
    void updateAlternativeEmailTrimsWhitespace() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        String alternativeEmail = "  trimmed@example.com  ";

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull("trimmed@example.com"))
            .thenReturn(Optional.empty());

        userService.updateAlternativeEmail(userId, alternativeEmail);

        assertThat(user.getAlternativeEmail()).isEqualTo("trimmed@example.com");
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, times(1)).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(
            "trimmed@example.com");
        verify(userRepository, times(1)).saveAndFlush(user);
    }

    @DisplayName("Should throw NotFoundException when user not found")
    @Test
    void updateAlternativeEmailUserNotFound() {
        UUID userId = UUID.randomUUID();
        String alternativeEmail = "newalt@example.com";

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.updateAlternativeEmail(userId, alternativeEmail)
        );

        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, never()).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Should throw ConflictException when alternative email exists for another user")
    @Test
    void updateAlternativeEmailConflict() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        String alternativeEmail = "existing@example.com";

        User existingUser = new User();
        existingUser.setId(otherUserId);
        existingUser.setEmail("other@example.com");
        existingUser.setAlternativeEmail(alternativeEmail);

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail))
            .thenReturn(Optional.of(existingUser));

        assertThrows(
            ConflictException.class,
            () -> userService.updateAlternativeEmail(userId, alternativeEmail)
        );

        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, times(1)).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Should allow updating alternative email when it exists for the same user")
    @Test
    void updateAlternativeEmailSameUser() {
        UUID userId = UUID.randomUUID();
        String alternativeEmail = "existing@example.com";
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        user.setAlternativeEmail(alternativeEmail);

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));
        when(userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail))
            .thenReturn(Optional.of(user));

        userService.updateAlternativeEmail(userId, alternativeEmail);

        assertThat(user.getAlternativeEmail()).isEqualTo(alternativeEmail);
        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, times(1)).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail);
        verify(userRepository, times(1)).saveAndFlush(user);
    }

    @DisplayName("Should throw IllegalArgumentException when alternative email format is invalid")
    @Test
    void updateAlternativeEmailInvalidFormat() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        String invalidEmail = "invalid@test";

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateAlternativeEmail(userId, invalidEmail))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Alternative email format is invalid");

        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, never()).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Should throw IllegalArgumentException when alternative email equals main email")
    @Test
    void updateAlternativeEmailSameAsMainEmail() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("original@example.com");
        String sameEmail = "original@example.com";

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateAlternativeEmail(userId, sameEmail))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Alternative email cannot be the same as the main email");

        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, never()).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Should throw IllegalArgumentException when alternative email equals main email (case insensitive)")
    @Test
    void updateAlternativeEmailSameAsMainEmailCaseInsensitive() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("Original@Example.com");
        String sameEmail = "original@example.com";

        when(userRepository.findByIdAndDeletedAtIsNull(userId))
            .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateAlternativeEmail(userId, sameEmail))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Alternative email cannot be the same as the main email");

        verify(userRepository, times(1)).findByIdAndDeletedAtIsNull(userId);
        verify(userRepository, never()).findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @DisplayName("Should get role by id successfully")
    @Test
    void getRoleByIdSuccess() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role();
        role.setId(roleId);
        role.setName("Test Role");

        when(roleRepository.findById(roleId))
            .thenReturn(Optional.of(role));

        Role result = userService.getRoleById(roleId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(roleId);
        assertThat(result.getName()).isEqualTo("Test Role");
        verify(roleRepository, times(1)).findById(roleId);
    }

    @DisplayName("Should throw NotFoundException when role not found")
    @Test
    void getRoleByIdNotFound() {
        UUID roleId = UUID.randomUUID();

        when(roleRepository.findById(roleId))
            .thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> userService.getRoleById(roleId)
        );

        verify(roleRepository, times(1)).findById(roleId);
    }

    @DisplayName("Find portal users with CJSM email and map to DTO")
    @Test
    void findPortalUsersWithCjsmEmailMapsToDTO() {
        // prepare a user with alternative email set
        var cjsmUser = new User();
        cjsmUser.setId(UUID.randomUUID());
        cjsmUser.setFirstName("CJSM");
        cjsmUser.setLastName("User");
        cjsmUser.setEmail("user@cjsm.net");
        cjsmUser.setAlternativeEmail("alt@cjsm.net");
        cjsmUser.setAppAccess(new HashSet<>());
        cjsmUser.setPortalAccess(new HashSet<>());

        when(userRepository.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(cjsmUser)));

        var page = userService.findPortalUsersWithCjsmEmail(Pageable.unpaged());

        assertThat(page.getTotalElements()).isEqualTo(1);
        var dto = page.get().toList().getFirst();
        assertThat(dto.getEmail()).isEqualTo(cjsmUser.getEmail());
        assertThat(dto.getAlternativeEmail()).isEqualTo(cjsmUser.getAlternativeEmail());
    }
}
