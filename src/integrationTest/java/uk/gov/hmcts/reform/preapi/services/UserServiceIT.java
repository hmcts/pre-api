package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessType;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class)
public class UserServiceIT {
    private static User userEntity;
    private static User portalUserEntity;
    private static User appUserEntity;
    private static AppAccess appAccessEntity;
    private static AppAccess appAccessEntity2;
    private static PortalAccess portalAccessEntity;
    private static PortalAccess portalAccessEntity2;
    private static Court court;
    private static Role role;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private AppAccessRepository appAccessRepository;

    @Autowired
    private PortalAccessRepository portalAccessRepository;

    @BeforeEach
    void setUp() {
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

        court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Mah Court");
        court.setCourtType(CourtType.CROWN);
        role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("Mah Role");

        appAccessEntity = new AppAccess();
        appAccessEntity.setId(UUID.randomUUID());
        appAccessEntity.setUser(userEntity);
        appAccessEntity.setCourt(court);
        appAccessEntity.setRole(role);
        appAccessEntity.setActive(true);

        appAccessEntity2 = new AppAccess();
        appAccessEntity2.setId(UUID.randomUUID());
        appAccessEntity2.setUser(appUserEntity);
        appAccessEntity2.setCourt(court);
        appAccessEntity2.setRole(role);
        appAccessEntity2.setActive(true);

        portalAccessEntity = new PortalAccess();
        portalAccessEntity.setId(UUID.randomUUID());
        portalAccessEntity.setUser(userEntity);
        portalAccessEntity.setPassword("mahpassword");

        portalAccessEntity2 = new PortalAccess();
        portalAccessEntity2.setId(UUID.randomUUID());
        portalAccessEntity2.setUser(portalUserEntity);
        portalAccessEntity2.setPassword("mahpassword");

        roleRepository.save(role);
        courtRepository.save(court);
        userRepository.saveAll(Set.of(userEntity, portalUserEntity, appUserEntity));
        appAccessRepository.saveAll(Set.of(appAccessEntity, appAccessEntity2));
        portalAccessRepository.saveAll(Set.of(portalAccessEntity, portalAccessEntity2));

        userEntity.setAppAccess(Set.of(appAccessEntity));
        userEntity.setPortalAccess(Set.of(portalAccessEntity));
        appUserEntity.setAppAccess(Set.of(appAccessEntity2));
        portalUserEntity.setPortalAccess(Set.of(portalAccessEntity2));
        userRepository.saveAll(Set.of(userEntity, portalUserEntity, appUserEntity));
    }

    @AfterEach
    void tearDown() {
        roleRepository.delete(role);
        courtRepository.delete(court);
        userRepository.deleteAll(Set.of(userEntity, portalUserEntity, appUserEntity));
        appAccessRepository.deleteAll(Set.of(appAccessEntity, appAccessEntity2));
        portalAccessRepository.deleteAll(Set.of(portalAccessEntity, portalAccessEntity2));
    }

    public static void mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

    public static void mockNonAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

    @Transactional
    @Test
    public void searchUsersAsAdmin() {
        mockAdminUser();
        userEntity.setDeletedAt(Timestamp.from(Instant.now()));
        userRepository.saveAndFlush(userEntity);

        var users = userService.findAllBy(null, null, null, null, null, null, null, false, Pageable.unpaged()).toList();

        Assertions.assertEquals(users.size(), 2);
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(portalUserEntity.getId())));
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(appUserEntity.getId())));
        Assertions.assertFalse(users.stream().anyMatch(user -> user.getId().equals(userEntity.getId())));

        var users2 = userService.findAllBy(null, null, null, null, null, null, null, true, Pageable.unpaged()).toList();

        Assertions.assertEquals(users2.size(), 3);
        Assertions.assertTrue(users2.stream().anyMatch(user -> user.getId().equals(portalUserEntity.getId())));
        Assertions.assertTrue(users2.stream().anyMatch(user -> user.getId().equals(appUserEntity.getId())));
        Assertions.assertTrue(users2.stream().anyMatch(user -> user.getId().equals(userEntity.getId())));
    }

    @Transactional
    @Test
    public void searchUsersAsNonAdmin() {
        mockNonAdminUser();
        userEntity.setDeletedAt(Timestamp.from(Instant.now()));
        userRepository.saveAndFlush(userEntity);

        var users = userService.findAllBy(null, null, null, null, null, null, null, false, Pageable.unpaged()).toList();

        Assertions.assertEquals(users.size(), 2);
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(portalUserEntity.getId())));
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(appUserEntity.getId())));
        Assertions.assertFalse(users.stream().anyMatch(user -> user.getId().equals(userEntity.getId())));

        var message = Assertions.assertThrows(
            AccessDeniedException.class,
            () -> userService.findAllBy(null, null, null, null, null, null, null, true, Pageable.unpaged()).toList()
        ).getMessage();

        Assertions.assertEquals(message, "Access Denied");

    }

    @Transactional
    @Test
    public void testGetUserByAccessType() {

        var resultApp = userService.findAllBy(
            null,
            null,
            null,
            null,
            null,
            null,
            AccessType.APP,
            false, PageRequest.of(0, 20)
        );
        Assertions.assertEquals(2, resultApp.getContent().size());
        var usersApp = resultApp.getContent().stream()
                                .sorted(Comparator.comparing(BaseUserDTO::getFirstName)).toList();
        Assertions.assertEquals(appUserEntity.getId(), usersApp.get(0).getId());
        Assertions.assertEquals(userEntity.getId(), usersApp.get(1).getId());

        var resultPortal = userService.findAllBy(
            null,
            null,
            null,
            null,
            null,
            null,
            AccessType.PORTAL,
            false, PageRequest.of(0, 20)
        );
        Assertions.assertEquals(2, resultPortal.getContent().size());
        var usersPortal = resultPortal.getContent().stream()
                                      .sorted(Comparator.comparing(BaseUserDTO::getFirstName)).toList();
        Assertions.assertEquals(userEntity.getFirstName(), usersPortal.get(0).getFirstName());
        Assertions.assertEquals(portalUserEntity.getId(), usersPortal.get(1).getId());

        var resultAll = userService.findAllBy(null, null, null, null, null, null, null, false, PageRequest.of(0, 20));
        Assertions.assertEquals(3, resultAll.getContent().size());
        var usersAll = resultAll.getContent().stream()
                                .sorted(Comparator.comparing(BaseUserDTO::getFirstName)).toList();
        Assertions.assertEquals(appUserEntity.getId(), usersAll.get(0).getId());
        Assertions.assertEquals(userEntity.getId(), usersAll.get(1).getId());
        Assertions.assertEquals(portalUserEntity.getId(), usersAll.get(2).getId());
    }
}
