package uk.gov.hmcts.reform.preapi.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserDetailService {

    private final AppAccessRepository appAccessRepository;

    @Autowired
    public UserDetailService(AppAccessRepository appAccessRepository) {
        this.appAccessRepository = appAccessRepository;
    }

    public UserDetails loadAppUserById(String id) {
        return validateUser(id)
            .orElseThrow(() -> new BadCredentialsException("Unauthorised user: " + id));
    }

    private Optional<UserDetails> validateUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Optional.empty();
        }

        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        var access = appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(id);

        return access.isEmpty()
            ? Optional.empty()
            : Optional.of(new UserDetails(access, AuthorityUtils.NO_AUTHORITIES));
    }
}
