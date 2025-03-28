package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public UserTermsAcceptedService(UserRepository userRepository,
                                    TermsAndConditionsRepository termsAndConditionsRepository,
                                    UserTermsAcceptedRepository userTermsAcceptedRepository) {
        this.userRepository = userRepository;
        this.termsAndConditionsRepository = termsAndConditionsRepository;
        this.userTermsAcceptedRepository = userTermsAcceptedRepository;
    }

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
    }
}
