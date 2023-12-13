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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = UserService.class)
public class UserServiceTest {
    private static User userEntity;
    private static AppAccess appAccessEntity;

    @MockBean
    private AppAccessRepository appAccessRepository;

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
}
