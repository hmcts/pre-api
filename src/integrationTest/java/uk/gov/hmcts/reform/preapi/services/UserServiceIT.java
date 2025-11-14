package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.AccessType;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

class UserServiceIT extends IntegrationTestBase {
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

    @BeforeEach
    void setUp() {
        userEntity = new User();
        userEntity.setId(UUID.randomUUID());
        userEntity.setFirstName("Example");
        userEntity.setLastName("Person");
        userEntity.setEmail("example@example.com");
        userEntity.setOrganisation("Example Org");
        entityManager.persist(userEntity);

        portalUserEntity = new User();
        portalUserEntity.setId(UUID.randomUUID());
        portalUserEntity.setFirstName("Portal");
        portalUserEntity.setLastName("Person");
        portalUserEntity.setEmail("portal@example.com");
        portalUserEntity.setOrganisation("Portal Org");
        entityManager.persist(portalUserEntity);

        appUserEntity = new User();
        appUserEntity.setId(UUID.randomUUID());
        appUserEntity.setFirstName("App");
        appUserEntity.setLastName("Person");
        appUserEntity.setEmail("app@example.com");
        appUserEntity.setOrganisation("App Org");
        entityManager.persist(appUserEntity);

        court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Mah Court");
        court.setCourtType(CourtType.CROWN);
        entityManager.persist(court);

        role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("Mah Role");
        entityManager.persist(role);

        entityManager.flush();

        appAccessEntity = new AppAccess();
        appAccessEntity.setId(UUID.randomUUID());
        appAccessEntity.setUser(userEntity);
        appAccessEntity.setCourt(court);
        appAccessEntity.setRole(role);
        appAccessEntity.setActive(true);
        appAccessEntity.setDefaultCourt(true);
        entityManager.persist(appAccessEntity);

        appAccessEntity2 = new AppAccess();
        appAccessEntity2.setId(UUID.randomUUID());
        appAccessEntity2.setUser(appUserEntity);
        appAccessEntity2.setCourt(court);
        appAccessEntity2.setRole(role);
        appAccessEntity2.setActive(true);
        appAccessEntity2.setDefaultCourt(true);
        entityManager.persist(appAccessEntity2);

        portalAccessEntity = new PortalAccess();
        portalAccessEntity.setId(UUID.randomUUID());
        portalAccessEntity.setUser(userEntity);
        entityManager.persist(portalAccessEntity);

        portalAccessEntity2 = new PortalAccess();
        portalAccessEntity2.setId(UUID.randomUUID());
        portalAccessEntity2.setUser(portalUserEntity);
        entityManager.persist(portalAccessEntity2);
    }

    @Transactional
    @Test
    void searchUsersAsAdmin() {
        mockAdminUser();
        userEntity.setDeletedAt(Timestamp.from(Instant.now()));
        entityManager.persist(userEntity);
        entityManager.flush();

        var users = userService.findAllBy(null, null, null, null, null, null, false, null, Pageable.unpaged()).toList();

        Assertions.assertEquals(users.size(), 2);
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(portalUserEntity.getId())));
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(appUserEntity.getId())));
        Assertions.assertFalse(users.stream().anyMatch(user -> user.getId().equals(userEntity.getId())));

        var users2 = userService.findAllBy(null, null, null, null, null, null, true, null, Pageable.unpaged()).toList();

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
        entityManager.persist(userEntity);
        entityManager.flush();

        var users = userService.findAllBy(null, null, null, null, null, null, false, null, Pageable.unpaged()).toList();

        Assertions.assertEquals(users.size(), 2);
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(portalUserEntity.getId())));
        Assertions.assertTrue(users.stream().anyMatch(user -> user.getId().equals(appUserEntity.getId())));
        Assertions.assertFalse(users.stream().anyMatch(user -> user.getId().equals(userEntity.getId())));

        var message = Assertions.assertThrows(
            AccessDeniedException.class,
            () -> userService.findAllBy(null, null, null, null, null, null, true, null, Pageable.unpaged()).toList()
        ).getMessage();

        Assertions.assertEquals(message, "Access Denied");

    }

    @Transactional
    @Test
    void testGetUserByAccessType() {
        var resultApp = userService.findAllBy(
            null,
            null,
            null,
            null,
            null,
            AccessType.APP,
            false,
            null,
            PageRequest.of(0, 20)
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
            AccessType.PORTAL,
            false,
            null,
            PageRequest.of(0, 20)
        );
        Assertions.assertEquals(2, resultPortal.getContent().size());
        var usersPortal = resultPortal.getContent().stream()
                                      .sorted(Comparator.comparing(BaseUserDTO::getFirstName)).toList();
        Assertions.assertEquals(userEntity.getFirstName(), usersPortal.get(0).getFirstName());
        Assertions.assertEquals(portalUserEntity.getId(), usersPortal.get(1).getId());

        var resultAll = userService.findAllBy(null, null, null, null, null, null, false,null, PageRequest.of(0, 20));
        Assertions.assertEquals(3, resultAll.getContent().size());
        var usersAll = resultAll.getContent().stream()
                                .sorted(Comparator.comparing(BaseUserDTO::getFirstName)).toList();
        Assertions.assertEquals(appUserEntity.getId(), usersAll.get(0).getId());
        Assertions.assertEquals(userEntity.getId(), usersAll.get(1).getId());
        Assertions.assertEquals(portalUserEntity.getId(), usersAll.get(2).getId());

        portalAccessEntity2.setStatus(AccessStatus.INACTIVE);
        var resultPortal2 = userService.findAllBy(
            null,
            null,
            null,
            null,
            null,
            AccessType.PORTAL,
            false,
            null,
            PageRequest.of(0, 20)
        );
        Assertions.assertEquals(1, resultPortal2.getContent().size());
        var usersPortal2 = resultPortal2.getContent().stream()
            .sorted(Comparator.comparing(BaseUserDTO::getFirstName)).toList();
        Assertions.assertEquals(userEntity.getId(), usersPortal2.get(0).getId());
    }

    @Transactional
    @Test
    void deleteUndeleteUserSuccess() {
        userService.deleteById(userEntity.getId());
        entityManager.flush();
        entityManager.refresh(userEntity);
        entityManager.refresh(appAccessEntity);
        entityManager.refresh(portalAccessEntity);

        Assertions.assertNotNull(userEntity.getDeletedAt());
        Assertions.assertNotNull(appAccessEntity.getDeletedAt());
        Assertions.assertFalse(appAccessEntity.isActive());
        Assertions.assertNotNull(portalAccessEntity.getDeletedAt());

        userService.undelete(userEntity.getId());
        entityManager.flush();
        entityManager.refresh(userEntity);
        entityManager.refresh(appAccessEntity);
        entityManager.refresh(portalAccessEntity);

        Assertions.assertNull(userEntity.getDeletedAt());
        Assertions.assertNull(appAccessEntity.getDeletedAt());
        Assertions.assertTrue(appAccessEntity.isActive());
        Assertions.assertNull(portalAccessEntity.getDeletedAt());
    }
}
