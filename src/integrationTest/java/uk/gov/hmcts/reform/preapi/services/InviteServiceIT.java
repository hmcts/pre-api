package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class InviteServiceIT extends IntegrationTestBase {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private InviteService inviteService;

    @Autowired
    UserService userService;

    private User user;
    private PortalAccess portalAccess;

    @BeforeEach
    void setUp() {

        user = HelperFactory.createUser("firstName",
                                        "lastName", "example@example.com", null, null, null);
        user.setId(UUID.randomUUID());
        entityManager.persist(user);

        portalAccess = HelperFactory.createPortalAccess(user, new Timestamp(System.currentTimeMillis()),
            AccessStatus.INVITATION_SENT, new Timestamp(System.currentTimeMillis()), null, null);
        portalAccess.setId(UUID.randomUUID());
        entityManager.persist(portalAccess);

        entityManager.flush();
    }

    @Test
    @Transactional
    public void findInvitesByUserId() {
        var invite = inviteService.findByUserId(user.getId());

        // Finds invited access
        assertThat(invite).isNotNull();
        assertThat(invite.getUserId()).isEqualTo(user.getId());
        assertThat(invite.getEmail()).isEqualTo(user.getEmail());
        assertThat(invite.getInvitedAt()).isAfter(Instant.now().minusSeconds(5));

        portalAccess = HelperFactory.createPortalAccess(user, new Timestamp(System.currentTimeMillis()),
            AccessStatus.ACTIVE, new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis()),null);
        entityManager.persist(portalAccess);
        entityManager.flush();
        entityManager.refresh(user);

        // Doesn't find active access
        invite = inviteService.findByUserId(user.getId());
        assertThat(invite).isNotNull();
    }

    @Test
    @Transactional
    public void redeemInvite() {
        var mockedUser = mockAdminUser();
        when(mockedUser.getUserId()).thenReturn(user.getId());

        userService.findByEmail(user.getEmail());
        assertThat(getUserByCache(user.getEmail())).isNotNull();

        inviteService.redeemInvite(user.getEmail());
        entityManager.flush();
        entityManager.refresh(user);
        assertThat(getUserByCache(user.getEmail())).isNull();

        Set<PortalAccess> portalAccessResult = user.getPortalAccess()
            .stream()
            .filter(access -> access.getStatus() == AccessStatus.ACTIVE)
            .collect(Collectors.toSet());
        assertThat(portalAccessResult).isNotNull();
        assertThat(portalAccessResult).hasSize(1);

        var acceptedPortalAccess = portalAccessResult.stream().findFirst().get();
        assertThat(acceptedPortalAccess).isNotNull();
        assertThat(acceptedPortalAccess.getUser().getId()).isEqualTo(user.getId());
        assertThat(acceptedPortalAccess.getId()).isEqualTo(portalAccess.getId());
        assertThat(acceptedPortalAccess.getRegisteredAt()).isAfter(Instant.now().minusSeconds(5));
    }

    private Cache.ValueWrapper getUserByCache(String email) {
        return cacheManager.getCache("users").get(email);
    }
}
