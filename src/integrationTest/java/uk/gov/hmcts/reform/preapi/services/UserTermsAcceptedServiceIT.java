package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public class UserTermsAcceptedServiceIT extends IntegrationTestBase {

    @Autowired
    private UserTermsAcceptedService userTermsAcceptedService;

    @Autowired
    UserService userService;

    @Autowired
    private CacheManager cacheManager;

    private User user;
    private TermsAndConditions termsAndConditions;

    @BeforeEach
    void setUp() {
        user = HelperFactory.createUser("firstName",
                                        "lastName", "example@example.com", null, null, null);
        user.setId(UUID.randomUUID());
        entityManager.persist(user);

        termsAndConditions = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "Content");
        entityManager.persist(termsAndConditions);

        entityManager.flush();
    }

    @Test
    @Transactional
    public void acceptTermsAndConditions() {
        var mockedUser = mockAdminUser();
        when(mockedUser.getUserId()).thenReturn(user.getId());
        userTermsAcceptedService.acceptTermsAndConditions(termsAndConditions.getId());

        entityManager.flush();
        entityManager.refresh(user);

        assertThat(user.getUserTermsAccepted()).isNotNull();
        assertThat(user.getUserTermsAccepted()).hasSize(1);

        var termsAccepted = user.getUserTermsAccepted().stream().findFirst().get();
        assertThat(termsAccepted).isNotNull();
        assertThat(termsAccepted.getUser().getId()).isEqualTo(user.getId());
        assertThat(termsAccepted.getTermsAndConditions().getId()).isEqualTo(termsAndConditions.getId());
        // very recently created
        assertThat(termsAccepted.getAcceptedAt()).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    @Transactional
    public void acceptTermsAndConditionsMultipleTimes() {
        var mockedUser = mockAdminUser();
        when(mockedUser.getUserId()).thenReturn(user.getId());
        userTermsAcceptedService.acceptTermsAndConditions(termsAndConditions.getId());
        userTermsAcceptedService.acceptTermsAndConditions(termsAndConditions.getId());
        userTermsAcceptedService.acceptTermsAndConditions(termsAndConditions.getId());

        entityManager.flush();
        entityManager.refresh(user);

        assertThat(user.getUserTermsAccepted()).isNotNull();
        assertThat(user.getUserTermsAccepted()).hasSize(3);

        var termsAccepted = user.getUserTermsAccepted();
        assertThat(termsAccepted).isNotNull();
        assertThat(termsAccepted.stream()
                       .allMatch(t -> t.getTermsAndConditions().getId()
                           .equals(termsAndConditions.getId()))).isTrue();
    }

    @Test
    @Transactional
    public void evictCacheAfterAcceptingTerms() {
        var mockedUser = mockAdminUser();
        when(mockedUser.getUserId()).thenReturn(user.getId());

        // Populate the cache before accepting terms
        userService.findByEmail(user.getEmail());

        var cache = cacheManager.getCache("users");
        assertThat(cache).isNotNull();
        assertThat(cache.get(user.getEmail())).isNotNull();

        userTermsAcceptedService.acceptTermsAndConditions(termsAndConditions.getId());
        entityManager.flush();
        entityManager.refresh(user);
        // After accepting terms, the cache should be evicted
        assertThat(cache.get(user.getEmail())).isNull();
    }

    @Test
    @Transactional
    public void cacheWontBeEvictedIfAcceptanceFails() throws Exception {
        var mockedUser = mockAdminUser();
        when(mockedUser.getUserId()).thenReturn(user.getId());

        // Populate the cache before accepting terms
        userService.findByEmail(user.getEmail());

        var cache = cacheManager.getCache("users");
        assertThat(cache).isNotNull();
        assertThat(cache.get(user.getEmail())).isNotNull();

        assertThatThrownBy(() -> userTermsAcceptedService.acceptTermsAndConditions(null))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasMessageContaining("The given id must not be null");;

        assertThat(cache.get(user.getEmail())).isNotNull();
    }
}
