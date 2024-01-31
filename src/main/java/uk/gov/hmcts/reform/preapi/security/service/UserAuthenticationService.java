package uk.gov.hmcts.reform.preapi.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserAuthenticationService {

    private final AppAccessRepository appAccessRepository;

    @Autowired
    public UserAuthenticationService(AppAccessRepository appAccessRepository) {
        this.appAccessRepository = appAccessRepository;
    }

    public UserAuthentication loadAppUserById(String id) {
        return validateUser(id)
            .orElseThrow(() -> new BadCredentialsException("Unauthorised user: " + id));
    }

    private Optional<UserAuthentication> validateUser(String accessId) {
        if (accessId == null || accessId.isEmpty()) {
            return Optional.empty();
        }

        UUID id;
        try {
            id = UUID.fromString(accessId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return appAccessRepository
            .findByIdAndDeletedAtNullAndUser_DeletedAtNull(id)
            .map(a -> new UserAuthentication(a, AuthorityUtils.NO_AUTHORITIES));
    }
}
