package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;

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

    @MockBean
    private AppAccessRepository appAccessRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private CourtRepository courtRepository;

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
}
