package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserTermsAcceptedRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserTermsAcceptedService {
    private final UserRepository userRepository;
    private final TermsAndConditionsRepository termsAndConditionsRepository;
    private final UserTermsAcceptedRepository userTermsAcceptedRepository;
    private final CacheManager cacheManager;

    @Autowired
    public UserTermsAcceptedService(UserRepository userRepository,
                                    TermsAndConditionsRepository termsAndConditionsRepository,
                                    UserTermsAcceptedRepository userTermsAcceptedRepository,
                                    CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.termsAndConditionsRepository = termsAndConditionsRepository;
        this.userTermsAcceptedRepository = userTermsAcceptedRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * Accepts the specified terms and conditions for a user.
     *
     * <p>
     * Retrieves the user from the database via security context. Gets the specified terms
     * and conditions then creates and persists a record of the user's acceptance of those terms.
     * </p>
     *
     * <p>
     * After persisting the acceptance, this method manually evicts the the cache populated by
     * {@link uk.gov.hmcts.reform.preapi.services.UserService#findByEmail(String)}.
     * This ensures that the user's updated terms acceptance is reflected in the cache on subsequent calls to
     * {@link uk.gov.hmcts.reform.preapi.services.UserService#findByEmail(String)}.
     * </p>
     * @param termsId the UUID of the terms and conditions to accept
     * @throws uk.gov.hmcts.reform.preapi.exception.NotFoundException if the user or terms and conditions are not found
     */
    @Transactional
    public void acceptTermsAndConditions(UUID termsId) {
        UUID userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();
        User user = userRepository.findById(userId)
            // this will not happen
            .orElseThrow(() -> new NotFoundException("User: " + userId));
        TermsAndConditions termsAndConditions = termsAndConditionsRepository.findById(termsId)
            .orElseThrow(() -> new NotFoundException("TermsAndConditions: " + termsId));

        UserTermsAccepted accepted = new UserTermsAccepted();
        accepted.setId(UUID.randomUUID());
        accepted.setUser(user);
        accepted.setTermsAndConditions(termsAndConditions);
        accepted.setAcceptedAt(Timestamp.from(Instant.now()));
        userTermsAcceptedRepository.save(accepted);

        var userEmail = user.getEmail(); // needed for cache eviction
        var cache = cacheManager.getCache("users");
        if (cache != null) {
            cache.evict(userEmail.toLowerCase());
        }
    }
}
