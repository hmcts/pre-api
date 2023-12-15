package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
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
}
