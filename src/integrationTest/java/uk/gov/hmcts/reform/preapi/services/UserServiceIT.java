package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.Set;
import java.util.UUID;

@SpringBootTest(classes = Application.class)
public class UserServiceIT {

    @Autowired
    UserService userService;
    UserRepository userRepository;
    RoleRepository roleRepository;
    CourtRepository courtRepository;
    AppAccessRepository appAccessRepository;
    PortalAccessRepository portalAccessRepository;

    @BeforeAll
    static void setUp() {
        var userEntity = new User();
        userEntity.setId(UUID.randomUUID());
        userEntity.setFirstName("Example");
        userEntity.setLastName("Person");
        userEntity.setEmail("example@example.com");
        userEntity.setOrganisation("Example Org");

        var portalUserEntity = new User();
        portalUserEntity.setId(UUID.randomUUID());
        portalUserEntity.setFirstName("Portal");
        portalUserEntity.setLastName("Person");
        portalUserEntity.setEmail("portal@example.com");
        portalUserEntity.setOrganisation("Portal Org");

        var appUserEntity = new User();
        appUserEntity.setId(UUID.randomUUID());
        appUserEntity.setFirstName("App");
        appUserEntity.setLastName("Person");
        appUserEntity.setEmail("app@example.com");
        appUserEntity.setOrganisation("App Org");

        var court = new Court();
        court.setId(UUID.randomUUID());
        var role = new Role();
        role.setId(UUID.randomUUID());

        var appAccessEntity = new AppAccess();
        appAccessEntity.setId(UUID.randomUUID());
        appAccessEntity.setUser(userEntity);
        appAccessEntity.setCourt(court);
        appAccessEntity.setRole(role);
        appAccessEntity.setActive(true);
        userEntity.setAppAccess(Set.of(appAccessEntity));

        var appAccessEntity2 = new AppAccess();
        appAccessEntity2.setId(UUID.randomUUID());
        appAccessEntity2.setUser(appUserEntity);
        appAccessEntity2.setCourt(court);
        appAccessEntity2.setRole(role);
        appAccessEntity2.setActive(true);
        appUserEntity.setAppAccess(Set.of(appAccessEntity2));

        var portalAccessEntity = new PortalAccess();
        portalAccessEntity.setId(UUID.randomUUID());
        portalAccessEntity.setUser(userEntity);
        userEntity.setPortalAccess(Set.of(portalAccessEntity));

        var portalAccessEntity2 = new PortalAccess();
        portalAccessEntity2.setId(UUID.randomUUID());
        portalAccessEntity2.setUser(portalUserEntity);
        portalUserEntity.setPortalAccess(Set.of(portalAccessEntity2));

        userRepository.saveAll(Set.of(userEntity, portalUserEntity, appUserEntity));
    }

    @Transactional
    @Test
    public void testGetAudit() {

    }
}
